package com.nutomic.syncthingandroid.http

import android.annotation.SuppressLint
import android.util.Log

import com.nutomic.syncthingandroid.util.Util

import java.io.File
import java.io.FileInputStream
import java.io.FileNotFoundException
import java.security.InvalidKeyException
import java.security.NoSuchAlgorithmException
import java.security.NoSuchProviderException
import java.security.SignatureException
import java.security.cert.CertificateException
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate

import javax.net.ssl.X509TrustManager

/*
 * TrustManager checking against the local Syncthing instance's https public key.
 *
 * Based on http://stackoverflow.com/questions/16719959#16759793
 *
 * The local Syncthing instance ships a self-signed certificate by default, which is verified by
 * pinning against the public key stored in https-cert.pem. A user may instead replace the HTTPS
 * certificate with one signed by a CA they trust at the Android OS level (see
 * https://github.com/100pangci/syncthing-android/issues/222); for that case we fall back to the
 * OS trust store when the self-signed pin does not match.
 *
 * Security scope: this trust manager is only ever wired into the loopback-pinned connection to the
 * local Syncthing instance (see forceLoopbackHost in ApiClient.kt). Because that connection cannot leave
 * the device, falling back to the OS trust store — which trusts user-installed CAs — does not open a
 * network MITM surface. Do not reuse this trust manager for any routable/remote connection.
 */
internal class SyncthingTrustManager(
    private val httpsCertPath: File
) : X509TrustManager {

    @SuppressLint("TrustAllX509TrustManager")
    override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) {
    }
    /**
     * Verifies certs against the public key of the local syncthing instance (self-signed pin).
     * If that fails, falls back to the Android OS trust store, which validates CA-signed
     * certificates against the system and user-installed certificate authorities.
     */
    override fun checkServerTrusted(certs: Array<X509Certificate>, authType: String) {
        try {
            verifyAgainstPinnedCert(certs)
        } catch (pinFailure: CertificateException) {
            // The presented certificate is not the pinned self-signed certificate. This is expected
            // when the user replaced the HTTPS certificate with a CA-signed one, so fall back to the
            // Android OS trust store instead of failing the connection outright. Logged at debug
            // level to avoid spamming logcat with the wrapped BAD_SIGNATURE on every request.
            Log.d(TAG, "Pinned certificate did not match, trying Android OS trust store.")
            val osTrustManager = Util.getOsTrustManager()
            if (osTrustManager == null) {
                throw pinFailure
            }
            osTrustManager.checkServerTrusted(certs, authType)
        }
    }

    /**
     * Verifies that every presented certificate is signed by the public key of the certificate
     * pinned in [httpsCertPath] (the certificate the local syncthing instance generated).
     */
    private fun verifyAgainstPinnedCert(certs: Array<X509Certificate>) {
        try {
            FileInputStream(httpsCertPath).use { inputStream ->
                val cf = CertificateFactory.getInstance("X.509")
                val ca = cf.generateCertificate(inputStream) as X509Certificate
                for (cert in certs) {
                    cert.verify(ca.publicKey)
                }
            }
        } catch (e: FileNotFoundException) {
            throw CertificateException("Untrusted Certificate!", e)
        } catch (e: NoSuchAlgorithmException) {
            throw CertificateException("Untrusted Certificate!", e)
        } catch (e: InvalidKeyException) {
            throw CertificateException("Untrusted Certificate!", e)
        } catch (e: NoSuchProviderException) {
            throw CertificateException("Untrusted Certificate!", e)
        } catch (e: SignatureException) {
            throw CertificateException("Untrusted Certificate!", e)
        }
    }

    /**
     * Returns an empty array: no CA issuers are pre-trusted by this manager, which verifies the
     * local instance's certificate against a pinned public key instead. Must not return null:
     * OkHttp rejects trust managers with null acceptedIssuers when building its trust root index.
     */
    override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()

    companion object {
        private const val TAG = "SyncthingTrustManager"
    }
}
