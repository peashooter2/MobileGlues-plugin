package com.fcl.plugin.mobileglues;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

public class AppInfoDialogBuilder extends MaterialAlertDialogBuilder {

    public AppInfoDialogBuilder(@NonNull Context context) {
        super(context);

        View view = LayoutInflater.from(context).inflate(R.layout.dialog_app_info, null);
        ((TextView) view.findViewById(R.id.info_version)).setText(BuildConfig.VERSION_NAME);

        setTitle(R.string.dialog_info);
        setView(view);
        setPositiveButton(R.string.dialog_positive, null);
        setNeutralButton(R.string.dialog_github, (dialog, id) -> context.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/MobileGL-Dev/MobileGlues-release"))));
    }
}
