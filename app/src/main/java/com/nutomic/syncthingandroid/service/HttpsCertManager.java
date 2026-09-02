package com.nutomic.syncthingandroid.service;

import android.content.Context;
import android.os.Handler;
import android.util.Log;

import androidx.annotation.Nullable;

import com.nutomic.syncthingandroid.service.SyncthingService.HttpsCertReplaceResult;
import com.nutomic.syncthingandroid.service.SyncthingService.OnHttpsCertReplaceResultListener;
import com.nutomic.syncthingandroid.service.SyncthingService.OnServiceStateChangeListener;
import com.nutomic.syncthingandroid.service.SyncthingService.State;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

/**
 * Owns the Web GUI HTTPS certificate replacement/reset logic:
 * stopping the binary, swapping or deleting the cert/key files, verifying that
 * Syncthing comes back online and rolling back to the previous certificate on failure.
 *
 * All lifecycle operations run on the service main thread, marshalled via {@link #replaceHttpsCertificate}.
 */
public class HttpsCertManager {

    private static final String TAG = "HttpsCertManager";

    /**
     * Backstop timeout for {@link #verifyRestartAndRollback} in case the state machine never reaches
     * a terminal state (e.g. the binary crashed via a path that doesn't transition to ERROR).
     */
    private static final long HTTPS_CERT_VERIFY_TIMEOUT_MS = 30000;

    private static final long DEFERRED_RETRY_DELAY_MS = 1000;

    private final SyncthingService mService;
    private final Handler mHandler;

    public HttpsCertManager(SyncthingService service, Handler mainHandler) {
        mService = service;
        mHandler = mainHandler;
    }

    /**
     * Replaces the Web GUI HTTPS certificate and key with the supplied PEM bytes, then restarts
     * Syncthing so the new files take effect. If the restart fails to bring the Web GUI back online,
     * the previous certificate and key are restored automatically.
     *
     * The bytes are expected to already be validated (see {@link com.nutomic.syncthingandroid.util.CertificateValidator}).
     * The whole start/stop lifecycle is marshalled onto the main thread because the binary
     * orchestration fields are only safe to touch there.
     */
    public void replaceHttpsCertificate(byte[] certPem, byte[] keyPem,
                                        OnHttpsCertReplaceResultListener listener) {
        mHandler.post(() -> doReplaceHttpsCertificate(certPem, keyPem, listener));
    }

    /**
     * Deletes the user-supplied HTTPS certificate/key so Syncthing regenerates a fresh self-signed
     * certificate at the next start, then restarts (with rollback on failure).
     */
    public void resetHttpsCertificate(OnHttpsCertReplaceResultListener listener) {
        mHandler.post(() -> doResetHttpsCertificate(listener));
    }

    private void doReplaceHttpsCertificate(byte[] certPem, byte[] keyPem,
                                           OnHttpsCertReplaceResultListener listener) {
        // shutdown() defers while STARTING; wait it out so our file writes don't race the binary.
        if (mService.getCurrentState() == State.STARTING) {
            mHandler.postDelayed(() -> doReplaceHttpsCertificate(certPem, keyPem, listener), DEFERRED_RETRY_DELAY_MS);
            return;
        }

        final File certFile = Constants.getHttpsCertFile(mService);
        final File keyFile = Constants.getHttpsKeyFile(mService);

        // Stop the binary so it releases the cert/key before we overwrite them.
        if (mService.getCurrentState() != State.DISABLED) {
            mService.shutdownToState(State.DISABLED);
        }

        final File certBak = backupFile(certFile);
        final File keyBak = backupFile(keyFile);

        try {
            writeBytesAtomic(certFile, certPem);
            writeBytesAtomic(keyFile, keyPem);
            restrictToOwner(keyFile);
        } catch (IOException e) {
            Log.e(TAG, "doReplaceHttpsCertificate: Failed to write new cert/key", e);
            restoreFile(certBak, certFile);
            restoreFile(keyBak, keyFile);
            if (mService.shouldRunAfterRestart()) {
                mService.launchStartupTask(SyncthingRunnable.Command.main);
            }
            listener.onResult(HttpsCertReplaceResult.FAILED, e.getMessage());
            return;
        }

        applyCertChangeWithVerify(certFile, keyFile, certBak, keyBak, listener);
    }

    private void doResetHttpsCertificate(OnHttpsCertReplaceResultListener listener) {
        if (mService.getCurrentState() == State.STARTING) {
            mHandler.postDelayed(() -> doResetHttpsCertificate(listener), DEFERRED_RETRY_DELAY_MS);
            return;
        }

        final File certFile = Constants.getHttpsCertFile(mService);
        final File keyFile = Constants.getHttpsKeyFile(mService);

        if (mService.getCurrentState() != State.DISABLED) {
            mService.shutdownToState(State.DISABLED);
        }

        final File certBak = backupFile(certFile);
        final File keyBak = backupFile(keyFile);
        // Removing the files makes syncthing generate a fresh self-signed certificate at startup.
        deleteQuietly(certFile);
        deleteQuietly(keyFile);

        applyCertChangeWithVerify(certFile, keyFile, certBak, keyBak, listener);
    }

    private void applyCertChangeWithVerify(File certFile, File keyFile,
                                           @Nullable File certBak, @Nullable File keyBak,
                                           OnHttpsCertReplaceResultListener listener) {
        if (mService.shouldRunAfterRestart()) {
            verifyRestartAndRollback(certFile, keyFile, certBak, keyBak, listener);
        } else {
            // Not currently meant to run; the new files will take effect on next start.
            deleteQuietly(certBak);
            deleteQuietly(keyBak);
            listener.onResult(HttpsCertReplaceResult.SUCCESS_PENDING_START, null);
        }
    }

    /**
     * Restarts the binary and watches the service state: success on reaching ACTIVE, failure on
     * ERROR / an abnormal STARTING&rarr;DISABLED transition (crashed binary) / a watchdog timeout.
     * On failure the backed-up cert/key are restored and a known-good instance is brought back up.
     */
    private void verifyRestartAndRollback(File certFile, File keyFile,
                                          @Nullable File certBak, @Nullable File keyBak,
                                          OnHttpsCertReplaceResultListener listener) {
        final boolean[] resolved = {false};
        final boolean[] sawStarting = {false};
        final OnServiceStateChangeListener[] verifyListener = new OnServiceStateChangeListener[1];
        final Runnable[] watchdog = new Runnable[1];

        final Runnable finishSuccess = () -> {
            deleteQuietly(certBak);
            deleteQuietly(keyBak);
            listener.onResult(HttpsCertReplaceResult.SUCCESS, null);
        };
        final Runnable finishFailure = () -> {
            restoreFile(certBak, certFile);
            restoreFile(keyBak, keyFile);
            // Bring the previous, known-good certificate back online.
            if (mService.getCurrentState() != State.DISABLED && mService.getCurrentState() != State.INIT) {
                mService.shutdownToState(State.INIT);
            }
            mService.launchStartupTask(SyncthingRunnable.Command.main);
            listener.onResult(HttpsCertReplaceResult.FAILED,
                    "Syncthing did not come online with the new certificate.");
        };

        watchdog[0] = () -> {
            if (resolved[0]) {
                return;
            }
            resolved[0] = true;
            mService.unregisterOnServiceStateChangeListener(verifyListener[0]);
            if (mService.getCurrentState() == State.ACTIVE) {
                finishSuccess.run();
            } else {
                finishFailure.run();
            }
        };

        verifyListener[0] = (state) -> {
            if (resolved[0]) {
                return;
            }
            if (state == State.STARTING) {
                sawStarting[0] = true;
                return;
            }
            final boolean success = (state == State.ACTIVE);
            final boolean failure = (state == State.ERROR) || (sawStarting[0] && state == State.DISABLED);
            if (!success && !failure) {
                return;
            }
            resolved[0] = true;
            mHandler.removeCallbacks(watchdog[0]);
            // Defer unregister + lifecycle work out of onServiceStateChange's listener iteration.
            mHandler.post(() -> {
                mService.unregisterOnServiceStateChangeListener(verifyListener[0]);
                if (success) {
                    finishSuccess.run();
                } else {
                    finishFailure.run();
                }
            });
        };

        // registerOnServiceStateChangeListener replays the current state (DISABLED) synchronously;
        // that is ignored because sawStarting is still false.
        mService.registerOnServiceStateChangeListener(verifyListener[0]);
        mHandler.postDelayed(watchdog[0], HTTPS_CERT_VERIFY_TIMEOUT_MS);
        mService.launchStartupTask(SyncthingRunnable.Command.main);
    }

    @Nullable
    private File backupFile(File file) {
        if (!file.exists()) {
            return null;
        }
        File bak = new File(file.getParentFile(), file.getName() + ".bak");
        deleteQuietly(bak);
        if (file.renameTo(bak)) {
            return bak;
        }
        Log.w(TAG, "backupFile: Failed to back up " + file.getName());
        return null;
    }

    private void restoreFile(@Nullable File bak, File target) {
        if (bak == null || !bak.exists()) {
            return;
        }
        deleteQuietly(target);
        if (!bak.renameTo(target)) {
            Log.w(TAG, "restoreFile: Failed to restore " + target.getName());
        }
    }

    private void deleteQuietly(@Nullable File file) {
        if (file != null && file.exists() && !file.delete()) {
            Log.w(TAG, "deleteQuietly: Failed to delete " + file.getName());
        }
    }

    private void writeBytesAtomic(File target, byte[] data) throws IOException {
        File tmp = new File(target.getParentFile(), target.getName() + ".tmp");
        try (FileOutputStream fos = new FileOutputStream(tmp)) {
            fos.write(data);
            fos.flush();
            fos.getFD().sync();
        }
        if (!tmp.renameTo(target)) {
            deleteQuietly(tmp);
            throw new IOException("Failed to rename " + tmp.getName() + " to " + target.getName());
        }
    }

    private void restrictToOwner(File file) {
        // Mirror syncthing core, which writes the HTTPS key with 0600 permissions.
        file.setReadable(false, false);
        file.setReadable(true, true);
        file.setWritable(false, false);
        file.setWritable(true, true);
        file.setExecutable(false, false);
    }
}
