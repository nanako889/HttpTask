package com.http.okhttp;

import android.content.Context;
import android.os.Handler;
import android.text.TextUtils;
import android.webkit.MimeTypeMap;

import com.google.gson.Gson;
import com.qbw.l.L;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.ref.WeakReference;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSession;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.X509TrustManager;

import okhttp3.Call;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.logging.HttpLoggingInterceptor;

/**
 * Created by Bond on 2016/4/13.
 */
public class HttpTask {

    public static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");
    public static final int FAILUE = -1;
    public static final String SYSTEM_ERROR = "system error";
    public static final String HTTP_ERROR = "http_error";
    private static boolean sDebug;
    private static Context sContext;
    private static OkHttpClient sOkHttpClient;
    private static Gson sGson;
    private static ICommonHeadersAndParameters sICommonHeadersAndParameters;
    private static ICommonErrorDeal sICommonErrorDeal;
    private static String sUrl;
    private static long sTimeDiff = 0;
    private static Handler sHandler;
    protected static L sLog = new L();
    private static String sKeyCode = "code";
    private static String sKeyMessage = "message";

    private enum BODY_TYPE {
        GET, POST, UPLOAD, PATCH, DELETE
    }

    private String mUrl;
    private String mMethod;
    private HashMap<String, Object> mParams;
    private List<String> mFilePaths;
    private Class mResponseClass;
    private FlowCallBack mFlowCallBack;
    private WeakReference<CallBack> mWrCallBack;
    private FlowCallBack mBeforeCallBack;
    private Object mBackParam;
    private Map<String, Object> mExtraParams;
    private FlowCallBack mBackgroundBeforeCallBack;
    private boolean mCanceled;
    private boolean mFinished;
    private BODY_TYPE mBodyType = BODY_TYPE.POST;
    private boolean mGlobalDeal = true;
    private boolean mNoCommonParam = false;

    public static void init(boolean isDebug, Context context, String url,
                            ICommonHeadersAndParameters iCommonHeadersAndParameters,
                            ICommonErrorDeal iCommonErrorDeal, String keyOfCode,
                            String keyOfMessage, String certificateAssetsName) {
        sDebug = isDebug;
        sLog.setFilterTag("[http]");
        sLog.setEnabled(isDebug);
        sContext = context;
        sUrl = url;
        sICommonHeadersAndParameters = iCommonHeadersAndParameters;
        sICommonErrorDeal = iCommonErrorDeal;
        sKeyCode = keyOfCode;
        sKeyMessage = keyOfMessage;
        OkHttpClient.Builder builder = new OkHttpClient.Builder();
        HttpLoggingInterceptor loggingInterceptor =
                new HttpLoggingInterceptor(new HttpLoggingInterceptor.Logger() {
                    @Override
                    public void log(String message) {
                        sLog.jsonV(message);
                    }
                });
        loggingInterceptor.setLevel(isDebug ? HttpLoggingInterceptor.Level.BODY :
                                            HttpLoggingInterceptor.Level.NONE);
        builder.addInterceptor(loggingInterceptor);
        builder.connectTimeout(30, TimeUnit.SECONDS);
        builder.readTimeout(30, TimeUnit.SECONDS);
        //builder.writeTimeout(30,TimeUnit.SECONDS);
        try {
            if (url.startsWith("https")) {
                InputStream inputStream = context.getAssets().open(certificateAssetsName);
                if (inputStream != null) {
                    CustomTrust.setTrust(builder, inputStream);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            sLog.e(e);
        }
        sOkHttpClient = builder.build();
        sGson = new Gson();
        sHandler = new Handler();
        if (sICommonHeadersAndParameters != null) {
            sICommonHeadersAndParameters.init(sContext);
        }
    }

    public static Context getContext() {
        return sContext;
    }

    public static HttpTask create(String method,
                                  HashMap<String, Object> params,
                                  Class responseClass,
                                  CallBack callBack) {
        return create(method, params, responseClass, null, null, callBack, null);
    }

    public static HttpTask create(
            String method,
            HashMap<String, Object> params,
            Class responseClass,
            Object backParam,
            FlowCallBack beforeCallBack,
            CallBack callBack,
            FlowCallBack flowCallBack) {
        return new HttpTask(
                sUrl,
                method,
                params,
                null,
                responseClass,
                backParam,
                beforeCallBack,
                callBack,
                flowCallBack);
    }

    private HttpTask(
            String url,
            String method,
            HashMap<String, Object> params,
            List<String> filePaths,
            Class responseClass,
            Object backParam,
            FlowCallBack beforeCallBack,
            CallBack callBack,
            FlowCallBack flowCallBack) {
        mUrl = url;
        mMethod = method;
        mParams = params;
        mFilePaths = filePaths;
        mResponseClass = responseClass;
        mBackParam = backParam;
        mBeforeCallBack = beforeCallBack;
        mWrCallBack = new WeakReference<>(callBack);
        mFlowCallBack = flowCallBack;
    }

    public HttpTask get() {
        mBodyType = BODY_TYPE.GET;
        return this;
    }

    public HttpTask post() {
        mBodyType = BODY_TYPE.POST;
        return this;
    }

    public HttpTask patch() {
        mBodyType = BODY_TYPE.PATCH;
        return this;
    }

    public HttpTask delete() {
        mBodyType = BODY_TYPE.DELETE;
        return this;
    }

    public HttpTask setUrl(String url) {
        mUrl = url;
        return this;
    }

    public HttpTask execute() {
        return execute(null);
    }

    private Request dealRequest() {
        try {

            if (sICommonHeadersAndParameters != null && mBodyType != BODY_TYPE.UPLOAD && !mNoCommonParam) {
                mParams = sICommonHeadersAndParameters.getParams(mMethod, mParams);
            }
            if (mParams == null) {
                mParams = new HashMap<>();
            }
            final Request.Builder reqBuilder = new Request.Builder();
            if (sICommonHeadersAndParameters != null) {
                Map<String, String> headers = sICommonHeadersAndParameters.getHeaders();
                if (headers != null && !headers.isEmpty()) {
                    for (Map.Entry<String, String> entry : headers.entrySet()) {
                        reqBuilder.addHeader(entry.getKey(), entry.getValue());
                    }
                }
            }
            if (mBodyType == BODY_TYPE.UPLOAD) {
                //unchecked
                MultipartBody.Builder bodyBuilder = new MultipartBody.Builder().setType(
                        MultipartBody.FORM);
                if (mFilePaths == null || mFilePaths.isEmpty()) {
                    if (sLog.isEnabled()) {
                        sLog.e("no file!");
                    }
                    return null;
                }
                File file;
                for (String path : mFilePaths) {
                    file = new File(path);
                    if (!file.exists()) {
                        sLog.w("file[%s] not exist", path);
                        continue;
                    }
                    bodyBuilder.addFormDataPart("file",
                                                "",
                                                RequestBody.create(MediaType.parse(getMimeTypeFromExtension(
                                                        getFileExtensionFromPath(path))),
                                                                   file));
                }

                Set<Map.Entry<String, Object>> entrySet = mParams.entrySet();
                for (Map.Entry<String, Object> entry : entrySet) {
                    bodyBuilder.addFormDataPart(entry.getKey(), entry.getValue().toString());
                }

                reqBuilder.url(mUrl).post(bodyBuilder.build());
            } else {
                //FormBody.Builder formBuilder;
                //StringBuilder urlBuilder;
                String url = mUrl + (!TextUtils.isEmpty(mMethod) ? "/" + mMethod : "");
                Set<Map.Entry<String, Object>> entrySet = mParams.entrySet();
                if (mBodyType == BODY_TYPE.POST) {
                    reqBuilder.url(url).post(RequestBody.create(JSON, getJsonParam()));
                } else if (mBodyType == BODY_TYPE.GET) {
                    StringBuilder urlBuilder = new StringBuilder(url);
                    urlBuilder.append("?");
                    for (Map.Entry<String, Object> entry : entrySet) {
                        urlBuilder.append(entry.getKey())
                                .append("=")
                                .append(entry.getValue())
                                .append("&");
                    }
                    urlBuilder = urlBuilder.replace(urlBuilder.length() - 1, urlBuilder.length(),
                                                    "");
                    reqBuilder.url(urlBuilder.toString());
                } else if (mBodyType == BODY_TYPE.PATCH) {
                    reqBuilder.url(url).patch(RequestBody.create(JSON, getJsonParam()));
                } else if (mBodyType == BODY_TYPE.DELETE) {
                    reqBuilder.url(url).delete(RequestBody.create(JSON, getJsonParam()));
                }
            }
            return reqBuilder.build();
        } catch (Exception e) {
            e.printStackTrace();
            sLog.e(e);
        }
        return null;
    }

    private String getJsonParam() {
        if (mParams == null || mParams.isEmpty()) {
            return "{}";
        }
        try {
            return sGson.toJson(mParams);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return "{}";
    }

    public Object executeSync() {
        return executeSync(null);
    }

    public Object executeSync(final IDataConverter iDataConverter) {
        Request request = dealRequest();
        if (request == null) {
            sLog.e("request == null");
            return null;
        }
        try {
            Response response = sOkHttpClient.newCall(request).execute();
            calculateTimeDiff(response);
            if (mResponseClass != null) {
                sLog.v(mResponseClass.getSimpleName());
            }
            if (response.isSuccessful()) {
                String result = response.body().string();
                if (iDataConverter == null) {
                    Map<String, Object> httpResponse = sGson.fromJson(result, Map.class);
                    String code = (String) httpResponse.get(sKeyCode);
                    String message = (String) httpResponse.get(sKeyMessage);
                    int iCode = Integer.parseInt(code);
                    if (iCode == 0) {
                        return sGson.fromJson(result, mResponseClass);
                    } else {
                        return httpResponse;
                    }
                } else {
                    return iDataConverter.doConvert(result, mResponseClass);
                }
            } else {
                sLog.e("http error status code[%d]", response.code());
                return null;
            }

        } catch (Exception e) {
            e.printStackTrace();
            sLog.e(e);
            return null;
        }
    }

    public HttpTask execute(final IDataConverter iDataConverter) {
        Request request = dealRequest();
        if (request == null) {
            sLog.e("request == null");
            return this;
        }
        onHttpStart();
        sOkHttpClient.newCall(request).enqueue(new okhttp3.Callback() {

            @Override
            public void onFailure(Call call, IOException e) {
                e.printStackTrace();
                sLog.e(e);
                onHttpFailed(FAILUE, getImportantMessage(e));
            }

            @Override
            public void onResponse(Call call, Response response) {
                try {
                    calculateTimeDiff(response);
                    if (mResponseClass != null) {
                        sLog.v(mResponseClass.getSimpleName());
                    }
                    if (response.isSuccessful()) {
                        String result = response.body().string();
                        if (iDataConverter == null) {
                            Map<String, Object> httpResponse = sGson.fromJson(result, Map.class);
                            String code = (String) httpResponse.get(sKeyCode);
                            String message = (String) httpResponse.get(sKeyMessage);
                            int iCode = Integer.parseInt(code);
                            if (iCode == 0) {
                                onHttpSuccess(result, sGson.fromJson(result, mResponseClass));
                            } else {
                                onHttpFailed(iCode, message);
                            }
                        } else {
                            onHttpSuccess(result, iDataConverter.doConvert(result, mResponseClass));
                        }
                    } else {
                        sLog.e("http error status code[%d]", response.code());
                        onHttpFailed(FAILUE, HTTP_ERROR);
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                    sLog.e(e);
                    onHttpFailed(FAILUE, getImportantMessage(e));
                } finally {
                    response.close();
                }

            }
        });
        return this;
    }

    public String getRealUrl() {
        return mUrl + (!TextUtils.isEmpty(mMethod) ? "/" + mMethod : "");
    }

    public HashMap<String, Object> getParams() {
        return mParams;
    }

    private void onHttpStart() {
        sLog.urlD(getRealUrl(), mParams);
        sHandler.post(new Runnable() {
            @Override
            public void run() {
                setFinished(false);
                CallBack callBack = mWrCallBack.get();
                if (null != callBack) {
                    callBack.onHttpStart(HttpTask.this);
                } else {
                    sLog.w("callBack was destroyed");
                }
            }
        });
    }

    private void onHttpSuccess(final String modelStr, final Object entity) {
        sLog.urlI(getRealUrl(), mParams);
        if (mBackgroundBeforeCallBack != null) {
            mBackgroundBeforeCallBack.onSuccess(this, entity, modelStr);
        }
        sHandler.post(new Runnable() {
            @Override
            public void run() {
                if (null != mBeforeCallBack) {
                    mBeforeCallBack.onSuccess(HttpTask.this, entity, modelStr);
                }
                CallBack callBack = mWrCallBack.get();
                if (null != callBack) {
                    callBack.onHttpSuccess(HttpTask.this, entity);
                } else {
                    sLog.w("callBack was destroyed");
                }
                if (null != mFlowCallBack) {
                    mFlowCallBack.onSuccess(HttpTask.this, entity, modelStr);
                }
                setFinished(true);
            }
        });
    }

    private void onHttpFailed(final int errorCode, final String message) {
        sLog.urlE(getRealUrl(), mParams);
        if (mBackgroundBeforeCallBack != null) {
            mBackgroundBeforeCallBack.onFailed(this, errorCode, message);
        }
        sHandler.post(new Runnable() {
            @Override
            public void run() {
                if (sICommonErrorDeal != null && mGlobalDeal) {
                    sICommonErrorDeal.onFailed(HttpTask.this, errorCode, message);
                }
                if (null != mBeforeCallBack) {
                    mBeforeCallBack.onFailed(HttpTask.this, errorCode, message);
                }
                CallBack callBack = mWrCallBack.get();
                if (null != callBack) {
                    callBack.onHttpFailed(HttpTask.this, errorCode, message);
                } else {
                    sLog.w("callBack was destroyed");
                }
                if (null != mFlowCallBack) {
                    mFlowCallBack.onFailed(HttpTask.this, errorCode, message);
                }
                setFinished(true);
            }
        });
    }

    public String getMethod() {
        return mMethod;
    }

    public WeakReference<CallBack> getWrCallBack() {
        return mWrCallBack;
    }

    public void cancel() {
        mCanceled = true;
    }

    public boolean isCanceled() {
        return mCanceled;
    }

    public boolean isFinished() {
        return mFinished;
    }

    private void setFinished(boolean b) {
        mFinished = b;
    }

    public HttpTask setGlobalDeal(boolean globalDeal) {
        mGlobalDeal = globalDeal;
        return this;
    }

    public HttpTask setNoCommonParam(boolean noCommonParam) {
        mNoCommonParam = noCommonParam;
        return this;
    }

    public HttpTask setBackgroundBeforeCallBack(FlowCallBack backgroundBeforeCallBack) {
        mBackgroundBeforeCallBack = backgroundBeforeCallBack;
        return this;
    }

    public boolean getBackParamBoolean() {
        if (mBackParam != null && mBackParam instanceof Boolean) {
            return (boolean) mBackParam;
        }
        return false;
    }

    public long getBackParamLong() {
        if (mBackParam != null && mBackParam instanceof Long) {
            return (Long) mBackParam;
        }
        return 0L;
    }

    public int getBackParamInt() {
        if (mBackParam != null && mBackParam instanceof Integer) {
            return (Integer) mBackParam;
        }
        return 0;
    }

    public String getBackParamString() {
        if (mBackParam != null && mBackParam instanceof String) {
            return (String) mBackParam;
        }
        return "";
    }

    public Object getBackParam() {
        return mBackParam;
    }

    public HttpTask addExtraParam(String key, Object param) {
        if (mExtraParams == null) {
            mExtraParams = new HashMap<>();
        }
        mExtraParams.put(key, param);
        return this;
    }

    public Object getExtraParam(String key) {
        return getExtraParamSize() <= 0 ? null : mExtraParams.get(key);
    }

    public int getExtraParamSize() {
        return mExtraParams == null ? 0 : mExtraParams.size();
    }

    public interface ICommonErrorDeal {
        void onFailed(HttpTask httpTask, int code, String message);
    }

    public interface CallBack {
        void onHttpStart(HttpTask httpTask);

        void onHttpSuccess(HttpTask httpTask, Object entity);

        void onHttpFailed(HttpTask httpTask, int errorCode, String message);
    }

    public static class SimpleCallBack implements CallBack {

        @Override
        public void onHttpStart(HttpTask httpTask) {

        }

        @Override
        public void onHttpSuccess(HttpTask httpTask, Object entity) {

        }

        @Override
        public void onHttpFailed(HttpTask httpTask, int errorCode, String message) {

        }
    }

    public interface FlowCallBack {
        void onSuccess(HttpTask httpTask, Object entity, String modelStr);

        void onFailed(HttpTask httpTask, int errorCode, String message);
    }

    public static class SimpleFlowCallBack implements FlowCallBack {

        @Override
        public void onSuccess(HttpTask httpTask, Object entity, String modelStr) {

        }

        @Override
        public void onFailed(HttpTask httpTask, int errorCode, String message) {

        }
    }

    public interface IDataConverter {
        Object doConvert(String dataStr, Class responseClass);
    }

    public interface ICommonHeadersAndParameters {

        void init(Context context);

        Map<String, String> getHeaders();

        HashMap<String, Object> getParams(String method, HashMap<String, Object> params);

    }

    private static void calculateTimeDiff(Response response) {
        String dateStr = response.header("Date");
        try {
            Date date = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz", Locale.US).parse(
                    dateStr);
            sTimeDiff = System.currentTimeMillis() - date.getTime();
            sLog.v("local and server time differ [%d]", sTimeDiff);
        } catch (Exception e) {
            sTimeDiff = 0;
            e.printStackTrace();
            sLog.e(e);
        }
    }

    /**
     * 获取服务器当前的时间
     */
    public static long getServerCurrentTimeMillis() {
        return System.currentTimeMillis() - sTimeDiff;
    }

    private String getFileExtensionFromPath(String path) {
        return path.substring(path.lastIndexOf(".") + 1);
    }

    private String getMimeTypeFromExtension(String extension) {
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension);
    }

    private static void trustAllCerts(OkHttpClient.Builder builder) {
        final X509TrustManager[] trustAllCerts = new X509TrustManager[]{
                new X509TrustManager() {
                    @Override
                    public void checkClientTrusted(java.security.cert.X509Certificate[] chain,
                                                   String authType) {
                    }

                    @Override
                    public void checkServerTrusted(java.security.cert.X509Certificate[] chain,
                                                   String authType) {
                    }

                    @Override
                    public X509Certificate[] getAcceptedIssuers() {
                        return new X509Certificate[]{};
                    }
                }
        };

        // Install the all-trusting trust manager
        try {
            SSLContext sslContext = SSLContext.getInstance("SSL");
            sslContext.init(null, trustAllCerts, new SecureRandom());
            // Create an ssl socket factory with our all-trusting manager
            final SSLSocketFactory sslSocketFactory = sslContext.getSocketFactory();
            builder.sslSocketFactory(sslSocketFactory, trustAllCerts[0]);
            builder.hostnameVerifier(new HostnameVerifier() {
                @Override
                public boolean verify(String hostname, SSLSession session) {
                    return true;
                }
            });

        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        } catch (KeyManagementException e) {
            e.printStackTrace();
        }
    }

    private String getImportantMessage(Exception exception) {
        String message = exception.getMessage();
        StringBuilder sb = new StringBuilder();
        if (sDebug) {
            sb.append("api:").append(mMethod).append(",");
        }
        sb.append(message == null ? SYSTEM_ERROR : message);
        return sb.toString();
    }
}
