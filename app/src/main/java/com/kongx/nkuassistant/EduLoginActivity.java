package com.kongx.nkuassistant;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Rect;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.text.Editable;
import android.text.InputType;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewTreeObserver;
import android.webkit.ConsoleMessage;
import android.webkit.WebChromeClient;
import android.webkit.WebView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.io.BufferedInputStream;
import java.net.CookieHandler;
import java.net.CookieManager;
import java.net.HttpCookie;
import java.net.SocketTimeoutException;
import java.util.Scanner;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.kongx.nkuassistant.Information.*;

public class EduLoginActivity extends AppCompatActivity implements Connectable {
    private static final long mBackPressThreshold = 3500;
    static String m_username;
    static String m_encryptedPassword;
    static String m_validateCode;
    private long mLastBackPress;
    private EditText mUsernameView;
    private EditText mPasswordView;
    private EditText mValidateView;
    private Button mLoginButton;
    private View mProgressView;
    private ImageView mValidateCode;
    private Switch mRemPass;
    private WebView webView;
    private boolean useRememberedPWD;
    static int lastHeightDiff = 0;

    @Override
    @SuppressLint("SetJavaScriptEnabled")
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_edu_login);
        getValidateCode(null);

        webView = new WebView(this);
        webView.getSettings().setJavaScriptEnabled(true);
        webView.setWebChromeClient(new WebChromeClient(){
            public boolean onConsoleMessage(ConsoleMessage cm)
            {
                m_encryptedPassword = cm.message();
                String strToPost = String.format(Strings.url_template, m_username, m_encryptedPassword, m_validateCode);
                new Connect(EduLoginActivity.this, RequestType.LOGIN, strToPost).execute(Information.webUrl + "/stdloginAction.do");
                return true;
            }
        });
        webView.loadUrl(Strings.url_webview);

        mValidateCode = (ImageView) findViewById(R.id.imageView_ValidateCode);
        mUsernameView = (EditText) findViewById(R.id.username);
        mPasswordView = (EditText) findViewById(R.id.password);
        mValidateView = (EditText) findViewById(R.id.ValidateCode);
        mRemPass = (Switch) findViewById(R.id.switch_RemPass);
        mLoginButton = (Button) findViewById(R.id.button_login);
        mLoginButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                attemptLogin();
            }
        });
        mValidateView.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView textView, int id, KeyEvent keyEvent) {
                attemptLogin();
                return true;
            }
        });
        mProgressView = findViewById(R.id.login_progress);
        SharedPreferences settings = getSharedPreferences(Information.PREFS_NAME, 0);
        mUsernameView.setText(settings.getString(Strings.setting_studentID, null));
        if (useRememberedPWD = settings.getBoolean(Strings.setting_remember_pwd, false)) {
            mRemPass.setChecked(true);
            mPasswordView.setSelectAllOnFocus(true);
            mPasswordView.setInputType(InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
            if(settings.getString(Strings.setting_password,null) != null){
                mPasswordView.setText(Strings.str_pwd_not_changed);
            }
        }

        mPasswordView.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {
                useRememberedPWD = false;
            }
            @Override
            public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {
                mPasswordView.setSelectAllOnFocus(false);
                mPasswordView.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
            }
            @Override
            public void afterTextChanged(Editable editable) {
                mPasswordView.removeTextChangedListener(this);
                mPasswordView.setSelection(mPasswordView.getText().length());
            }
        });
    }

    @Override
    public void onBackPressed() {
        long currentTime = System.currentTimeMillis();
        if (Math.abs(currentTime - mLastBackPress) > mBackPressThreshold) {
            Toast.makeText(getApplicationContext(), R.string.press_back_again_to_exit, Toast.LENGTH_SHORT).show();
            mLastBackPress = currentTime;
        } else finish();
    }

    public void getValidateCode(View view) {
        new Connect(this, RequestType.VALIDATE_CODE, null).execute(Information.webUrl+Strings.url_validate_code);
    }

    @Override
    public void onTaskComplete(Object o, int type) {
        if(o == null){
            Log.e("EduLoginActivity", "Connectable returned null?");
        }else if(o.getClass() == BufferedInputStream.class) {
            BufferedInputStream is = (BufferedInputStream) o;
            Pattern pattern;
            Matcher matcher;
            switch (type) {
                case RequestType.VALIDATE_CODE:
                    Bitmap pic = BitmapFactory.decodeStream(is);
                    if (pic == null) {
                        Log.e("EduLoginActivity", "Decode is finished but picture is not valid.");
                    } else {
                        Bitmap resized = Bitmap.createBitmap(pic, 0, 0, pic.getWidth() / 2, pic.getHeight());
                        resized.setWidth(75);
                        mValidateCode.setImageBitmap(resized);
                    }
                    break;
                case RequestType.LOGIN: {
                    String returnString = new Scanner(is, "GB2312").useDelimiter("\\A").next();
                    pattern = Pattern.compile("<LI>(.+)\\n(?=</LI>)");
                    matcher = pattern.matcher(returnString);
                    if (matcher.find()) {
                        Toast.makeText(getApplicationContext(), "登录失败，" + matcher.group(1), Toast.LENGTH_SHORT).show();
                        mValidateView.setText("");
                        getValidateCode(null);
                        showProgress(false);
                    } else {
                        new Connect(this, RequestType.USER_INFO, null).execute(Information.webUrl + Strings.url_student_info);
                        SharedPreferences settings = getSharedPreferences(Information.PREFS_NAME, MODE_PRIVATE);
                        SharedPreferences.Editor settingEditor = settings.edit();
                        CookieManager cookieManager = (CookieManager) CookieHandler.getDefault();
                        for (HttpCookie httpCookie : cookieManager.getCookieStore().getCookies()) {
                            if (httpCookie.getName().equals("JSESSIONID")   ) {
                                settingEditor.putString("JSESSIONID", httpCookie.getValue());
                                break;
                            }
                        }
                        settingEditor.putBoolean(Strings.setting_remember_pwd, mRemPass.isChecked());
                        settingEditor.putString(Strings.setting_studentID, m_username);
                        settingEditor.putString(Strings.setting_password, m_encryptedPassword);
                        settingEditor.apply();

                    }
                    break;
                }
                case RequestType.USER_INFO: {
                    String returnString = new Scanner(is, "GB2312").useDelimiter("\\A").next();
                    pattern = Pattern.compile("<td width=\"10%\" bgcolor=\"#ffffff\" class=\"NavText\">(.+)</td>");
                    matcher = pattern.matcher(returnString);
                    if (matcher.find()) Information.id = matcher.group(1);
                    if (matcher.find()) Information.name = matcher.group(1);
                    pattern = Pattern.compile("<td  bgcolor=\"#ffffff\" class=\"NavText\" colspan=\"3\">(.+)</td>");
                    matcher = pattern.matcher(returnString);
                    if (matcher.find()) Information.facultyName = matcher.group(1);
                    if (matcher.find()) Information.majorName = matcher.group(1);

                    SharedPreferences settings = getSharedPreferences(Information.PREFS_NAME, 0);
                    SharedPreferences.Editor editor = settings.edit();
                    editor.putString(Strings.setting_student_name, Information.name);
                    editor.putString(Strings.setting_student_faculty, Information.facultyName);
                    editor.putString(Strings.setting_student_major, Information.majorName);
                    editor.apply();

                    Intent intent = new Intent(getApplicationContext(), IndexActivity.class);
                    intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                    startActivity(intent);
                    finish();
                    break;
                }
                default:
                    break;
            }
        }else if(o.getClass() == Integer.class){
            Integer code = (Integer)o;
            if(code == 302){
                //TODO:Login to 202.113.18.106
                Toast.makeText(getApplicationContext(), Strings.str_gateway_redirected , Toast.LENGTH_SHORT).show();
            }
        }else if(o.getClass() == SocketTimeoutException.class){
            Toast.makeText(getApplicationContext(), Strings.str_socket_time_out , Toast.LENGTH_SHORT).show();
        }
    }

    private void attemptLogin() {
        mUsernameView.setError(null);
        mPasswordView.setError(null);

        String username = mUsernameView.getText().toString();
        String password = mPasswordView.getText().toString();
        String validateCode = mValidateView.getText().toString();

        boolean cancel = true;
        View focusView = null;

        if (TextUtils.isEmpty(username)) {
            mUsernameView.setError(getString(R.string.error_studentID_required));
            focusView = mUsernameView;
        } else if (username.length() != 7) {
            mUsernameView.setError(getString(R.string.error_invalid_studentID));
            focusView = mUsernameView;
        } else if (TextUtils.isEmpty(password)) {
            mPasswordView.setError(getString(R.string.error_password_required));
            focusView = mPasswordView;
        } else if (TextUtils.isEmpty(validateCode)) {
            mValidateView.setError(getString(R.string.error_validate_required));
            focusView = mValidateView;
        } else if (validateCode.length() != 4) {
            mValidateView.setError(getString(R.string.error_invalid_validate));
            focusView = mValidateView;
        } else cancel = false;

        if (cancel) {
            focusView.requestFocus();
            return;
        }

        m_username = username;
        m_validateCode = validateCode;
        SharedPreferences settings = getSharedPreferences(Information.PREFS_NAME, 0);
        if (useRememberedPWD) {
            String strToPost = String.format(Strings.url_template, m_username, settings.getString(Strings.setting_password, null), m_validateCode);
            new Connect(EduLoginActivity.this, RequestType.LOGIN, strToPost).execute(Information.webUrl + "/stdloginAction.do");
        } else webView.loadUrl("javascript:encryption(\"" + password + "\")");
        showProgress(true);
    }

    private void showProgress(final boolean show) {
        mProgressView.setVisibility(show ? View.VISIBLE : View.GONE);
        mProgressView.animate().setDuration(10).alpha(
                show ? 1 : 0).setListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                mProgressView.setVisibility(show ? View.VISIBLE : View.GONE);
            }
        });
    }

    private static class RequestType {
        static final int VALIDATE_CODE = 0;
        static final int LOGIN = 1;
        static final int USER_INFO = 2;
    }
}

