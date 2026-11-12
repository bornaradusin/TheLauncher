package app.thelauncher.compatlib.eleven;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import app.thelauncher.compatlib.ActivityManagerCompat;
import app.thelauncher.compatlib.ActivityOptionsCompat;
import app.thelauncher.compatlib.ten.QuickstepCompatFactoryVQ;

@RequiresApi(30)
public class QuickstepCompatFactoryVR extends QuickstepCompatFactoryVQ {

    @NonNull
    @Override
    public ActivityManagerCompat getActivityManagerCompat() {
        return new ActivityManagerCompatVR();
    }

    @NonNull
    @Override
    public ActivityOptionsCompat getActivityOptionsCompat() {
        return new ActivityOptionsCompatVR();
    }
}
