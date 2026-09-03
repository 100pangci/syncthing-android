package com.nutomic.syncthingandroid;

import com.nutomic.syncthingandroid.activities.MainActivity;
import com.nutomic.syncthingandroid.onboarding.OnboardingActivity;
import com.nutomic.syncthingandroid.receiver.AppConfigReceiver;
import com.nutomic.syncthingandroid.service.RunConditionMonitor;
import com.nutomic.syncthingandroid.service.EventPoller;
import com.nutomic.syncthingandroid.service.RestApi;
import com.nutomic.syncthingandroid.service.SyncthingRunnable;
import com.nutomic.syncthingandroid.service.SyncthingService;
import com.nutomic.syncthingandroid.settings.SettingsActivity;

import javax.inject.Singleton;

import dagger.Component;

@Singleton
@Component(modules = {SyncthingModule.class})
public interface DaggerComponent {
    void inject(AppConfigReceiver appConfigReceiver);
    void inject(EventPoller eventPoller);
    void inject(MainActivity activity);
    void inject(OnboardingActivity onboardingActivity);
    void inject(RestApi restApi);
    void inject(RunConditionMonitor runConditionMonitor);
    void inject(SettingsActivity settingsActivity);
    void inject(SyncthingApp app);
    void inject(SyncthingRunnable syncthingRunnable);
    void inject(SyncthingService service);
}
