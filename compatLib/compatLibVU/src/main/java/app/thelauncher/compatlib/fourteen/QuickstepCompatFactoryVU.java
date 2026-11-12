package app.thelauncher.compatlib.fourteen;

import android.window.RemoteTransition;
import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import app.thelauncher.compatlib.ActivityManagerCompat;
import app.thelauncher.compatlib.ActivityOptionsCompat;
import app.thelauncher.compatlib.RemoteTransitionCompat;
import app.thelauncher.compatlib.thirteen.QuickstepCompatFactoryVT;

@RequiresApi(34)
public class QuickstepCompatFactoryVU extends QuickstepCompatFactoryVT {

    @NonNull
    @Override
    public ActivityManagerCompat getActivityManagerCompat() {
        return new ActivityManagerCompatVU();
    }

    @NonNull
    @Override
    public ActivityOptionsCompat getActivityOptionsCompat() {
        return new ActivityOptionsCompatVU();
    }

    @NonNull
    @Override
    public RemoteTransitionCompat getRemoteTransitionCompat() {
        return RemoteTransition::new;
    }
}
