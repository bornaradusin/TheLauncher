package app.thelauncher.data.iconoverride

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.PrimaryKey
import app.thelauncher.icons.picker.IconPickerItem
import com.android.launcher3.util.ComponentKey

@Entity
data class IconOverride(
    @PrimaryKey val target: ComponentKey,
    @Embedded val iconPickerItem: IconPickerItem,
)
