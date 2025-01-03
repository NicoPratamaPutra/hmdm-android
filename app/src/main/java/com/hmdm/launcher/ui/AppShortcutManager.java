package com.hmdm.launcher.ui;

import android.app.Activity;
import android.app.admin.DevicePolicyManager;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.os.Build;
import android.widget.Toast;

import com.hmdm.launcher.AdminReceiver;
import com.hmdm.launcher.Const;
import com.hmdm.launcher.helper.SettingsHelper;
import com.hmdm.launcher.json.Application;
import com.hmdm.launcher.util.AppInfo;
import com.hmdm.launcher.util.Utils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class AppShortcutManager {

    private static AppShortcutManager instance;

    public static AppShortcutManager getInstance() {
        if (instance == null) {
            instance = new AppShortcutManager();
        }
        return instance;
    }

    public int getInstalledAppCount(Context context, boolean bottom) {
        Map<String, Application> requiredPackages = new HashMap();
        Map<String, Application> requiredLinks = new HashMap();
        getConfiguredApps(context, bottom, requiredPackages, requiredLinks);
        List<ApplicationInfo> packs = context.getPackageManager().getInstalledApplications(0);
        if (packs == null) {
            return requiredLinks.size();
        }
        // Calculate applications
        int packageCount = 0;
        for(int i = 0; i < packs.size(); i++) {
            ApplicationInfo p = packs.get(i);
            if (context.getPackageManager().getLaunchIntentForPackage(p.packageName) != null &&
                    requiredPackages.containsKey(p.packageName)) {
                packageCount++;
            }
        }
        return requiredLinks.size() + packageCount;
    }

    public void startKiosksMode(Activity activity, String[] appPackages) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            DevicePolicyManager dpm = (DevicePolicyManager) activity.getSystemService(Context.DEVICE_POLICY_SERVICE);
//            if (mDevicePolicyManager.isAdminActive(AdminReceiver.getComponentName(activity))) {
            if (Utils.isDeviceOwner(activity)) {//mDevicePolicyManager.isDeviceOwnerApp(activity.getPackageName())
//                String[] appPackages =  {activity.getPackageName(), "com.wilmar.itmmobile", "com.hmdm.pager", "com.byteexperts.texteditor", "com.android.settings", "com.sec.android.app.launcher"};
                dpm.setLockTaskPackages(AdminReceiver.getComponentName(activity), appPackages);//new ComponentName(getPackageName(), "com.hmdm.launcher.AdminReceiver")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    dpm.setLockTaskFeatures(AdminReceiver.getComponentName(activity), DevicePolicyManager.LOCK_TASK_FEATURE_SYSTEM_INFO | DevicePolicyManager.LOCK_TASK_FEATURE_NOTIFICATIONS | DevicePolicyManager.LOCK_TASK_FEATURE_HOME);
                }
                activity.startLockTask();
            } else {
                Toast.makeText(activity, "Please set app as device owner", Toast.LENGTH_LONG).show();
            }
//            else {
//                Intent intent = new Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN);
//                intent.putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, new ComponentName(this, AdminReceiver.class));
//                startActivityForResult(intent, 0);
//            }
        }
    }

    public List<AppInfo> getInstalledApps(Context context, boolean bottom) {
        Map<String, Application> requiredPackages = new HashMap();
        Map<String, Application> requiredLinks = new HashMap();
        getConfiguredApps(context, bottom, requiredPackages, requiredLinks);

        List<AppInfo> appInfos = new ArrayList<>();
        List<ApplicationInfo> packs = context.getPackageManager().getInstalledApplications(0);
        if (packs == null) {
            return new ArrayList<AppInfo>();
        }
        ArrayList<String> packageNames = new ArrayList<>();
        // First we display app icons
        for(int i = 0; i < packs.size(); i++) {
            ApplicationInfo p = packs.get(i);
            if ( context.getPackageManager().getLaunchIntentForPackage(p.packageName) != null &&
                    requiredPackages.containsKey( p.packageName ) ) {
                Application app = requiredPackages.get(p.packageName);
                AppInfo newInfo = new AppInfo();
                newInfo.type = AppInfo.TYPE_APP;
                newInfo.keyCode = app.getKeyCode();
                newInfo.name = app.getIconText() != null ? app.getIconText() : p.loadLabel(context.getPackageManager()).toString();
                newInfo.packageName = p.packageName;
                packageNames.add(p.packageName);
                newInfo.iconUrl = app.getIcon();
                newInfo.screenOrder = app.getScreenOrder();
                newInfo.longTap = app.isLongTap() ? 1 : 0;
                appInfos.add(newInfo);
            }
        }
        packageNames.add(context.getPackageName());
//        packageNames.add(Const.SETTINGS_PACKAGE_NAME);
//        packageNames.add(Const.SYSTEM_UI_PACKAGE_NAME);
//        packageNames.add("com.sec.android.app.launcher");
        startKiosksMode((Activity) context, packageNames.toArray(new String[0]));

        // Then we display weblinks
        for (Map.Entry<String, Application> entry : requiredLinks.entrySet()) {
            AppInfo newInfo = new AppInfo();
            newInfo.type = entry.getValue().getType().equals(Application.TYPE_INTENT) ? AppInfo.TYPE_INTENT : AppInfo.TYPE_WEB;
            newInfo.keyCode = entry.getValue().getKeyCode();
            newInfo.name = entry.getValue().getIconText();
            newInfo.url = entry.getValue().getUrl();
            newInfo.iconUrl = entry.getValue().getIcon();
            newInfo.screenOrder = entry.getValue().getScreenOrder();
            newInfo.useKiosk = entry.getValue().isUseKiosk() ? 1 : 0;
            newInfo.intent = entry.getValue().getIntent();
            appInfos.add(newInfo);
        }

        // Apply manually set order
        Collections.sort(appInfos, new AppInfosComparator());

        return appInfos;
    }

    private void getConfiguredApps(Context context, boolean bottom, Map<String, Application> requiredPackages, Map<String, Application> requiredLinks) {
        SettingsHelper config = SettingsHelper.getInstance( context );
        if ( config.getConfig() != null ) {
            List< Application > applications = SettingsHelper.getInstance( context ).getConfig().getApplications();
            for ( Application application : applications ) {
                if (application.isShowIcon() && !application.isRemove() && (bottom == application.isBottom())) {
                    if (application.getType() == null || application.getType().equals(Application.TYPE_APP)) {
                        requiredPackages.put(application.getPkg(), application);
                    } else if (application.getType().equals(Application.TYPE_WEB)) {
                        requiredLinks.put(application.getUrl(), application);
                    } else if (application.getType().equals(Application.TYPE_INTENT)) {
                        requiredLinks.put(application.getIntent(), application);
                    }
                }
            }
        }
    }

    public class AppInfosComparator implements Comparator<AppInfo> {
        @Override
        public int compare(AppInfo o1, AppInfo o2) {
            if (o1.screenOrder == null) {
                if (o2.screenOrder == null) {
                    return 0;
                }
                return 1;
            }
            if (o2.screenOrder == null) {
                return -1;
            }
            return Integer.compare(o1.screenOrder, o2.screenOrder);
        }
    }

}
