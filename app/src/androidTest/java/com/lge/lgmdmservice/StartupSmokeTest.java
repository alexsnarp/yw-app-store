package com.lge.lgmdmservice;

import android.content.Intent;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.assertNotNull;

@RunWith(AndroidJUnit4.class)
public class StartupSmokeTest {
    @Test public void mainActivityStartsWithoutCrashing() {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity -> assertNotNull(activity.getWindow().getDecorView()));
        }
    }

    @Test public void independentSettingsActivityStarts() {
        try (ActivityScenario<SettingsActivity> scenario = ActivityScenario.launch(SettingsActivity.class)) {
            scenario.onActivity(activity -> assertNotNull(activity.getWindow().getDecorView()));
        }
    }

    @Test public void appDetailsActivityStarts() {
        Intent intent = new Intent(androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().getTargetContext(), AppDetailsActivity.class);
        intent.putExtra("yw_detail_json", "{\"name\":\"Smoke test\",\"packageName\":\"com.example.smoke\",\"version\":\"1\",\"iconUrl\":\"\",\"apkUrl\":\"\"}");
        try (ActivityScenario<AppDetailsActivity> scenario = ActivityScenario.launch(intent)) {
            scenario.onActivity(activity -> assertNotNull(activity.getWindow().getDecorView()));
        }
    }
}
