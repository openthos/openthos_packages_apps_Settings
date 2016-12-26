/*
 * Copyright (C) 2011 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.settings.accountmanager;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.net.Uri;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.Preference.OnPreferenceClickListener;
import android.preference.PreferenceScreen;
import android.util.Log;
import android.view.textservice.SpellCheckerInfo;
import android.view.textservice.SpellCheckerSubtype;
import android.view.textservice.TextServicesManager;
import android.widget.Switch;

import com.android.settings.R;
import com.android.settings.SettingsActivity;
import com.android.settings.SettingsPreferenceFragment;
import com.android.settings.widget.SwitchBar;
import com.android.settings.widget.SwitchBar.OnSwitchChangeListener;
import android.content.SharedPreferences;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.content.ContentValues;
import android.content.ContentResolver;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.View.OnClickListener;
import android.widget.EditText;
import android.widget.TextView;
import android.app.Activity;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.HashMap;
import java.util.Map;
import android.os.Handler;
import android.os.Message;
import android.widget.Toast;
import android.database.sqlite.SQLiteDatabase;
import android.content.Context;
import java.net.URLEncoder;
import java.util.HashMap;
import java.util.Map;
import java.util.ArrayList;
import java.util.List;
import android.content.ContentValues;
import java.io.FileWriter;
import java.io.File;
import org.apache.http.HttpStatus;
import org.apache.http.NameValuePair;
import org.apache.http.message.BasicNameValuePair;

public class OpenthosIDSettings extends SettingsPreferenceFragment
        implements  OnPreferenceClickListener {
    private static final String TAG = OpenthosIDSettings.class.getSimpleName();
    private static final boolean DBG = false;

    private static final String KEY_OPENTHOS_ID = "openthos_id";
    private static final String KEY_OPENTHOS_ID_EMAIL = "openthos_id_email";

    private Preference mOpenthosIDPref;
    private AlertDialog mDialog = null;
    private TextServicesManager mTsm;

    private ContentResolver mResolver;

    private Preference mBindPref;
    private Preference mUnbundPref;
    private static final String KEY_BIND = "openthos_bind";
    private static final String KEY_UNBUND = "openthos_unbund";
    private String openthosID;
    private String password;
    private final Map<String, String> params = new HashMap<String, String>();
    private int result;
    private Handler mHandler;
    private final String encode = "utf-8";
    private static final int RG_REQUEST = 0;
    private static String CODE_WRONG_USERNAME = "1002";
    private static String CODE_WRONG_PASSWORD = "1001";
    private static String CODE_SUCCESS = "1000";
    private String mCookie = "";
    public static final int MSG_GET_CSRF = 0x1001;
    public static final int MSG_GET_CSRF_OK = 0x1002;
    public static final int MSG_REGIST_SEAFILE = 0x1003;
    public static final int MSG_REGIST_SEAFILE_OK = 0x1004;

    @Override
    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        Log.i("fragmentWhich","--------------openthosID");
        addPreferencesFromResource(R.xml.openthos_id_prefs);
        mOpenthosIDPref = findPreference(KEY_OPENTHOS_ID);
        mOpenthosIDPref.setOnPreferenceClickListener(this);

        mResolver = getActivity().getContentResolver();
        Uri uriQuery = Uri.parse("content://com.otosoft.tools.myprovider/openthosID");
        Cursor cursor = mResolver.query(uriQuery, null, null, null, null);
        if (cursor != null) {
            while(cursor.moveToNext()) {
                String id = cursor.getString(cursor.getColumnIndex("openthosID"));
                mOpenthosIDPref.setSummary(id);
            }
        }

        mBindPref = findPreference(KEY_BIND);
        mBindPref.setOnPreferenceClickListener(this);
        mUnbundPref = findPreference(KEY_UNBUND);
        mUnbundPref.setOnPreferenceClickListener(this);

        mHandler = new Handler() {

            @Override
            public void handleMessage (Message msg) {
                switch (msg.what) {
                    case HttpURLConnection.HTTP_OK:
                        Bundle b = msg.getData();
                        String result = b.getString("result");
                        String code = result.split(":")[1].split("\"")[1].trim();
                        if (CODE_WRONG_USERNAME.equals(code)) {
                            Toast.makeText(getActivity(),
                                    getText(R.string.toast_openthos_id_wrong),
                                    Toast.LENGTH_SHORT).show();
                        } else if (CODE_WRONG_PASSWORD.equals(code)) {
                            Toast.makeText(getActivity(),
                                    getText(R.string.toast_openthos_password_wrong),
                                    Toast.LENGTH_SHORT).show();
                        } else {
                            mHandler.sendEmptyMessage(MSG_GET_CSRF);
                            Uri uriInsert =
                                Uri.parse("content://com.otosoft.tools.myprovider/openthosID");
                            ContentValues values = new ContentValues();
                            values.put("openthosID", openthosID);
                            values.put("password", password);
                            mResolver.insert(uriInsert, values);
                        }
                        break;
                    case MSG_GET_CSRF:
                        getCsrf();
                        break;
                    case MSG_GET_CSRF_OK:
                        mCookie = (String) msg.obj;
                        mHandler.sendEmptyMessage(MSG_REGIST_SEAFILE);
                        break;
                    case MSG_REGIST_SEAFILE:
                        registSeafile();
                        break;
                    case MSG_REGIST_SEAFILE_OK:
                        break;
                    default:
                        Toast.makeText(getActivity(),
                                getText(R.string.toast_network_not_connect),
                                Toast.LENGTH_SHORT).show();
                        break;
                }
            }
        };

    }

    @Override
    public void onResume() {
        super.onResume();
    }

    @Override
    public void onPause() {
        super.onPause();
    }

    @Override
    public boolean onPreferenceClick(final Preference pref) {
        if (pref == mOpenthosIDPref) {
            Intent intent = new Intent();
            intent.setAction("android.intent.action.VIEW");
            Uri content_url = Uri.parse("http://dev.openthos.org/?q=user/register");
            intent.setData(content_url);
            startActivity(intent);
            return true;
        } else if (pref == mBindPref) {
            View viewBind = LayoutInflater.from(getActivity()).inflate(R.layout.dialog_bind, null);
            final EditText userID_bind = (EditText) viewBind.findViewById(R.id.dialog_name);
            final EditText userPassword_bind = (EditText) viewBind.
                                               findViewById(R.id.dialog_name_bind);
            AlertDialog.Builder builder_bind = new AlertDialog.Builder(getActivity());
            builder_bind.setTitle(R.string.account_bind);
            builder_bind.setView(viewBind);
            builder_bind.setPositiveButton(R.string.account_dialog_ok,
                new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        // TODO Auto-generated method stub
                        openthosID = userID_bind.getText().toString().trim();
                        password = userPassword_bind.getText().toString().trim();
                        params.put("username", openthosID);
                        params.put("password", password);
                        submitPostData(params, encode);
                        dialog.dismiss();
                    }
                });
            builder_bind.setNegativeButton(R.string.account_dialog_cancel, null);
            builder_bind.show();
        } else if (pref == mUnbundPref) {
            AlertDialog.Builder builder_unbund = new AlertDialog.Builder(getActivity());
            builder_unbund.setMessage(R.string.account_judge);
            builder_unbund.setPositiveButton(R.string.account_yes,
                new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                            // TODO Auto-generated method stub
                        Uri uriDelete =
                            Uri.parse("content://com.otosoft.tools.myprovider/openthosID");
                        mResolver.delete(uriDelete,null,null);
                        dialog.dismiss();
                    }
                });
            builder_unbund.setNegativeButton(R.string.account_no,
                new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                         dialog.dismiss();
                    }
                });
            builder_unbund.create().show();
        }
        return false;
    }

    public void submitPostData(final Map<String, String> params, final String encode) {
        final byte[] data = getRequestData(params, encode).toString().getBytes();
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    HttpURLConnection httpURLConnection =
                            (HttpURLConnection) HttpUtils.getHttpsURLConnection(
                                    "http://dev.openthos.org/?q=check/userinfo");
                    httpURLConnection.setConnectTimeout(3000);
                    httpURLConnection.setDoInput(true);
                    httpURLConnection.setDoOutput(true);
                    httpURLConnection.setRequestMethod("POST");
                    httpURLConnection.setUseCaches(false);
                    //set the request body type is text
                    httpURLConnection.setRequestProperty("Content-Type",
                            "application/x-www-form-urlencoded");
                    //set the request body length
                    httpURLConnection.setRequestProperty("Content-Length",
                            String.valueOf(data.length));
                    //get the ouput stream and write to the service
                    OutputStream outputStream = httpURLConnection.getOutputStream();
                    outputStream.write(data);

                    int response = httpURLConnection.getResponseCode();
                    //get the service response
                    String data = new String();
                    if (response == HttpURLConnection.HTTP_OK) {
                        InputStream inptStream = httpURLConnection.getInputStream();
                        data = dealResponseResult(inptStream);
                    }
                    Message msg = Message.obtain();
                    msg.what = response;
                    Bundle bundle = new Bundle();
                    bundle.putString("result", data);
                    msg.setData(bundle);
                    mHandler.sendMessage(msg);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }).start();
    }

    public static StringBuffer getRequestData(Map<String, String> params, String encode) {
        StringBuffer stringBuffer = new StringBuffer();
        try {
            for (Map.Entry<String, String> entry : params.entrySet()) {
                stringBuffer.append(entry.getKey())
                            .append("=")
                            .append(URLEncoder.encode(entry.getValue(), encode))
                            .append("&");
            }
            stringBuffer.deleteCharAt(stringBuffer.length() - 1);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return stringBuffer;
    }

    public static String dealResponseResult(InputStream inputStream) {
        String resultData = null;
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        byte[] data = new byte[1024];
        int len = 0;
        try {
            while ((len = inputStream.read(data)) != -1) {
                byteArrayOutputStream.write(data, 0, len);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        resultData = new String(byteArrayOutputStream.toByteArray());
        return resultData;
    }

    private static int convertSubtypeIndexToDialogItemId(final int index) { return index + 1; }
    private static int convertDialogItemIdToSubtypeIndex(final int item) { return item - 1; }

    private void getCsrf() {
        RequestThread thread = new RequestThread(mHandler,
           "https://dev.openthos.org/accounts/register/", null, RequestThread.RequestType.GET);
        thread.start();
    }

    private void registSeafile() {
        List<NameValuePair> list = new ArrayList<>();
        list.add(new BasicNameValuePair("csrfmiddlewaretoken", mCookie.split("=")[1].trim()));
        list.add(new BasicNameValuePair("email", openthosID));
        list.add(new BasicNameValuePair("password1", password));
        RequestThread thread = new RequestThread(mHandler,
               "https://dev.openthos.org/accounts/register/", list, RequestThread.RequestType.POST,
                mCookie);
        thread.start();
    }
}
