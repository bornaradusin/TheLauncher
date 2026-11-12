package app.thelauncher.compatlib.sixteen;

import android.window.RemoteTransition;
import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import app.thelauncher.compatlib.ActivityManagerCompat;
import app.thelauncher.compatlib.ActivityOptionsCompat;
import app.thelauncher.compatlib.RemoteTransitionCompat;
import app.thelauncher.compatlib.fifteen.QuickstepCompatFactoryVV;

@RequiresApi(36)
public class QuickstepCompatFactoryVBaklava extends QuickstepCompatFactoryVV {

    @NonNull
    @Override
    public ActivityManagerCompat getActivityManagerCompat() {
        return new ActivityManagerCompatVBaklava();
    }

    @NonNull
    @Override
    public ActivityOptionsCompat getActivityOptionsCompat() {
        return new ActivityOptionsCompatVBaklava();
    }

    @NonNull
    @Override
    public RemoteTransitionCompat getRemoteTransitionCompat() {
        return RemoteTransition::new;
    }
}
