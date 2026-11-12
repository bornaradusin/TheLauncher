package app.thelauncher.compatlib.fifteen;

import android.window.RemoteTransition;
import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import app.thelauncher.compatlib.ActivityManagerCompat;
import app.thelauncher.compatlib.ActivityOptionsCompat;
import app.thelauncher.compatlib.RemoteTransitionCompat;
import app.thelauncher.compatlib.fourteen.QuickstepCompatFactoryVU;

@RequiresApi(35)
public class QuickstepCompatFactoryVV extends QuickstepCompatFactoryVU {

    @NonNull
    @Override
    public ActivityManagerCompat getActivityManagerCompat() {
        return new ActivityManagerCompatVV();
    }

    @NonNull
    @Override
    public ActivityOptionsCompat getActivityOptionsCompat() {
        return new ActivityOptionsCompatVV();
    }

    @NonNull
    @Override
    public RemoteTransitionCompat getRemoteTransitionCompat() {
        return RemoteTransition::new;
    }
}
