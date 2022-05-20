package com.http.okhttp;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Handler;
import android.text.TextUtils;
import android.webkit.MimeTypeMap;

import com.google.gson.Gson;
import com.qbw.l.L;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.ref.WeakReference;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.TimeUnit;

import okhttp3.Call;
import okhttp3.FormBody;
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
    public static final int NETWORK_INVALID = -2;
    public static String SYSTEM_ERROR = "system error";
    public static String NETWORK_ERROR = "network error";
    public static final String NETWORK_ERROR_CASE = "Failed to connect to";
    public static final String NETWORK_ERROR_CASE_1 = "Connection reset";
    public static final List<String> NETWORK_ERROR_CASE_LIST = new ArrayList<>();
    private static boolean sDebug;
    private static Context sContext;
    private static OkHttpClient sOkHttpClient;
    private static Gson sGson;
    private static ICommonHeadersAndParameters sICommonHeadersAndParameters;
    private static ICommonErrorDeal sICommonErrorDeal;
    private static RealExceptionCallback sRealExceptionCallback;
    private static ClientServerTimeDiffCallback sClientServerTimeDiffCallback;
    private static String sUrl;
    private static long sTimeDiff = 0;
    private static Handler sHandler;
    protected static L sLog = new L();
    private static Class sResponseClass;
    private static Type sType = Type.RAW_METHOD_APPEND_URL;

    private String mUrl;
    private String mMethod;
    private Map<String, Object> mParams;
    private Map<String, String> mFiles;
    private Map<String, String> mHeaders;
    private Class mResponseClass;
    private FlowCallBack mAfterCallBack;
    private WeakReference<CallBack> mWrCallBack;
    private boolean mWeakReferenceCallback = false;
    private CallBack mCallBack;
    private FlowCallBack mBeforeCallBack;
    private Object mBackParam;
    private Map<String, Object> mExtraParams;
    private FlowCallBack mBackgroundBeforeCallBack;
    private boolean mCanceled;
    private boolean mFinished;
    private BODY_TYPE mBodyType = BODY_TYPE.POST;
    private boolean mGlobalDeal = true;
    private boolean mNoCommonParam = false;
    private long mStartTimestamp;
    private String mDefaultFileExtension = "jpg";

    private enum BODY_TYPE {
        GET, POST, UPLOAD, PATCH, DELETE
    }

    public enum Type {
        RAW_METHOD_APPEND_URL,
        FORM_METHOD_IN_FORMBODY
    }

    public static void init(boolean isDebug,
                            Context context,
                            String url,
                            ICommonHeadersAndParameters iCommonHeadersAndParameters,
                            ICommonErrorDeal iCommonErrorDeal,
                            Class responseClass,
                            String certificateAssetsName) {
        init(isDebug,
                context,
                url,
                iCommonHeadersAndParameters,
                iCommonErrorDeal,
                responseClass,
                certificateAssetsName,
                Type.RAW_METHOD_APPEND_URL);
    }

    public static void init(boolean isDebug,
                            Context context,
                            String url,
                            ICommonHeadersAndParameters iCommonHeadersAndParameters,
                            ICommonErrorDeal iCommonErrorDeal,
                            Class responseClass,
                            String certificateAssetsName,
                            Type type) {
        init(isDebug, context, url, iCommonHeadersAndParameters, iCommonErrorDeal, responseClass,
                certificateAssetsName, type, new Param());
    }

    public static void init(boolean isDebug,
                            Context context,
                            String url,
                            ICommonHeadersAndParameters iCommonHeadersAndParameters,
                            ICommonErrorDeal iCommonErrorDeal,
                            Class responseClass,
                            String certificateAssetsName,
                            Type type, Param param) {
        sDebug = isDebug;
        sLog.setFilterTag("[http]");
        sLog.setEnabled(isDebug);
        sContext = context;
        sUrl = url;
        sICommonHeadersAndParameters = iCommonHeadersAndParameters;
        sICommonErrorDeal = iCommonErrorDeal;
        sResponseClass = responseClass;
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
        builder.connectTimeout(param.mConnectTimeout, TimeUnit.SECONDS);
        builder.readTimeout(param.mReadTimeout, TimeUnit.SECONDS);
        builder.writeTimeout(param.mWriteTimeout, TimeUnit.SECONDS);
        try {
            if (url.startsWith("https")) {
                if (!TextUtils.isEmpty(certificateAssetsName)) {
                    InputStream inputStream = context.getAssets().open(certificateAssetsName);
                    CustomTrust.setTrust(builder, inputStream);
                } else {
                    sLog.w("notice that you choose trust all certificates");
                    CustomTrust.trustAllCerts(builder);
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
        sType = type;
        NETWORK_ERROR_CASE_LIST.add(NETWORK_ERROR_CASE);
        NETWORK_ERROR_CASE_LIST.add(NETWORK_ERROR_CASE_1);
    }

    public static Context getContext() {
        return sContext;
    }

    public static HttpTask create(String method,
                                  Map<String, Object> params,
                                  Class responseClass,
                                  CallBack callBack) {
        return create(method, params, responseClass, null, null, callBack, null);
    }

    public static HttpTask create(String method,
                                  Map<String, Object> params,
                                  Class responseClass,
                                  Object backParam,
                                  CallBack callBack) {
        return create(method, params, responseClass, backParam, null, callBack, null);
    }

    public static HttpTask create(String method,
                                  Map<String, Object> params,
                                  Class responseClass,
                                  Object backParam,
                                  FlowCallBack beforeCallBack,
                                  CallBack callBack,
                                  FlowCallBack afterCallBack) {
        return new HttpTask(sUrl,
                method,
                params,
                responseClass,
                backParam,
                beforeCallBack,
                callBack,
                afterCallBack);
    }

    private HttpTask(String url,
                     String method,
                     Map<String, Object> params,
                     Class responseClass,
                     Object backParam,
                     FlowCallBack beforeCallBack,
                     CallBack callBack,
                     FlowCallBack afterCallBack) {
        mUrl = url;
        mMethod = method;
        mParams = params;
        mResponseClass = responseClass;
        mBackParam = backParam;
        mBeforeCallBack = beforeCallBack;
        if (mWeakReferenceCallback) {
            mWrCallBack = new WeakReference<>(callBack);
        } else {
            mCallBack = callBack;
        }
        mAfterCallBack = afterCallBack;
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

    public HttpTask upload(Map<String, String> files) {
        mBodyType = BODY_TYPE.UPLOAD;
        mFiles = files;
        setNoCommonParam(true);
        return this;
    }

    public HttpTask setUrl(String url) {
        mUrl = url;
        return this;
    }

    public HttpTask setBackParam(Object backParam) {
        mBackParam = backParam;
        return this;
    }

    public HttpTask setBeforeCallBack(FlowCallBack beforeCallBack) {
        mBeforeCallBack = beforeCallBack;
        return this;
    }

    public HttpTask setAfterCallBack(FlowCallBack afterCallBack) {
        mAfterCallBack = afterCallBack;
        return this;
    }

    public HttpTask setWeakReferenceCallback(boolean weakReferenceCallback) {
        mWeakReferenceCallback = weakReferenceCallback;
        return this;
    }

    public HttpTask execute() {
        return execute(null);
    }

    private Request dealRequest() {
        try {

            if (sICommonHeadersAndParameters != null && !mNoCommonParam) {
                mParams = sICommonHeadersAndParameters.getParams(mMethod, mParams);
            }
            if (mParams == null) {
                mParams = new TreeMap<>();
            }
            final Request.Builder reqBuilder = new Request.Builder();
            if (sICommonHeadersAndParameters != null) {
                Map<String, String> headers = sICommonHeadersAndParameters.getHeaders(mMethod,
                        mParams);
                mHeaders = headers;
                if (headers != null && !headers.isEmpty()) {
                    for (Map.Entry<String, String> entry : headers.entrySet()) {
                        reqBuilder.addHeader(entry.getKey(), entry.getValue());
                    }
                }
            }
            if (mBodyType == BODY_TYPE.UPLOAD) {
                MultipartBody.Builder bodyBuilder = new MultipartBody.Builder().setType(
                        MultipartBody.FORM);
                if (mFiles == null || mFiles.isEmpty()) {
                    sLog.e("no file!");
                    return null;
                }
                File file;
                Iterator<Map.Entry<String, String>> params = mFiles.entrySet().iterator();
                Map.Entry<String, String> param;
                String filePath;
                while (params.hasNext()) {
                    param = params.next();
                    filePath = param.getValue();
                    file = new File(filePath);
                    if (!file.exists()) {
                        sLog.w("file[%s] not exist", filePath);
                        continue;
                    }
                    String key = param.getKey();
                    String fileExtension = getFileExtensionFromPath(filePath);
                    String mineType = getMimeTypeFromExtension(fileExtension);
                    String fileName;
                    if (!TextUtils.isEmpty(mineType)) {
                        fileName = key + "." + fileExtension;
                    } else {
                        fileExtension = mDefaultFileExtension;
                        mineType = getMimeTypeFromExtension(fileExtension);
                        fileName = key + "." + fileExtension;
                    }
                    sLog.d("add upload file[%s], key,fileName[%s],fileExtension[%s],mineType[%s]",
                            key,
                            fileName,
                            fileExtension,
                            mineType);
                    bodyBuilder.addFormDataPart(key,
                            fileName,
                            RequestBody.create(MediaType.parse(mineType),
                                    file));
                }

                Set<Map.Entry<String, Object>> entrySet = mParams.entrySet();
                for (Map.Entry<String, Object> entry : entrySet) {
                    bodyBuilder.addFormDataPart(entry.getKey(), entry.getValue().toString());
                }
                String url = getRealUrl();
                reqBuilder.url(url).post(bodyBuilder.build());
            } else {
                String url = getRealUrl();
                Set<Map.Entry<String, Object>> entrySet = mParams.entrySet();
                if (mBodyType == BODY_TYPE.POST) {
                    if (sType == Type.RAW_METHOD_APPEND_URL) {
                        reqBuilder.url(url).post(RequestBody.create(JSON, getJsonParam()));
                    } else if (sType == Type.FORM_METHOD_IN_FORMBODY) {
                        FormBody.Builder formBuilder = new FormBody.Builder();
                        for (Map.Entry<String, Object> entry : entrySet) {
                            if (!(entry.getValue() instanceof String)) {
                                throw new RuntimeException("when use form，value must be string！！！");
                            }
                            formBuilder.add(entry.getKey(), entry.getValue().toString());
                        }
                        reqBuilder.url(url).post(formBuilder.build());
                    }
                } else if (mBodyType == BODY_TYPE.GET || mBodyType == BODY_TYPE.DELETE) {
                    StringBuilder urlBuilder = new StringBuilder(url);
                    urlBuilder.append("?");
                    for (Map.Entry<String, Object> entry : entrySet) {
                        urlBuilder.append(entry.getKey())
                                .append("=")
                                .append(entry.getValue())
                                .append("&");
                    }
                    urlBuilder = urlBuilder.replace(urlBuilder.length() - 1,
                            urlBuilder.length(),
                            "");
                    reqBuilder.url(urlBuilder.toString());
                    if (mBodyType == BODY_TYPE.GET) {
                        reqBuilder.get();
                    } else {
                        reqBuilder.delete();
                    }
                } else if (mBodyType == BODY_TYPE.PATCH) {
                    reqBuilder.url(url).patch(RequestBody.create(JSON, getJsonParam()));
                }
            }
            return reqBuilder.build();
        } catch (Exception e) {
            e.printStackTrace();
            sLog.e(e);
        }
        return null;
    }

    public String getRealUrl() {
        if (sType == Type.FORM_METHOD_IN_FORMBODY) {
            return mUrl;
        }
        return mUrl + (!TextUtils.isEmpty(mMethod) ? mMethod : "");
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

    public HttpTask execute(final IDataConverter iDataConverter) {
        Request request = dealRequest();
        if (request == null) {
            sLog.e("request == null");
            return this;
        }
        mStartTimestamp = System.currentTimeMillis();
        onHttpStart();
        sOkHttpClient.newCall(request).enqueue(new okhttp3.Callback() {

            @Override
            public void onFailure(Call call, IOException e) {
                e.printStackTrace();
                sLog.e(e);
                String msg = e.getMessage();
                if (!TextUtils.isEmpty(msg) && isNetworkError(msg)) {
                    onHttpFailed(NETWORK_INVALID, NETWORK_ERROR);
                } else {
                    onHttpFailed(FAILUE, SYSTEM_ERROR);
                }
                if (sRealExceptionCallback != null) {
                    sRealExceptionCallback.onHttpTaskRealException(HttpTask.this, FAILUE, msg);
                }
            }

            @Override
            public void onResponse(Call call, Response response) {
                try {
                    calculateTimeDiff(response);
                    if (mResponseClass != null) {
                        sLog.v("response[%s]", mResponseClass.getName());
                    }
                    if (sResponseClass != null) {
                        sLog.v("sResponse[%s]", sResponseClass.getName());
                    }
                    if (response.isSuccessful()) {
                        String result = response.body().string();
                        if (iDataConverter == null) {
                            Object httpResponse = sGson.fromJson(result, sResponseClass);
                            if (!(httpResponse instanceof IHttpResponse)) {
                                sLog.e("result[%s]", result);
                                throw new RuntimeException("sResponseClass must implements " +
                                        "IHttpResponse");
                            }
                            IHttpResponse iHttpResponse = (IHttpResponse) httpResponse;
                            if (iHttpResponse.getCode() == 0) {
                                onHttpSuccess(result, sGson.fromJson(result, mResponseClass));
                            } else {
                                onHttpFailed(iHttpResponse.getCode(), iHttpResponse.getMessage());
                            }
                        } else {
                            onHttpSuccess(result, iDataConverter.doConvert(result, mResponseClass));
                        }
                    } else {
                        sLog.e("http error status code[%d]", response.code());
                        onHttpFailed(response.code(), "");
                        if (sRealExceptionCallback != null) {
                            sRealExceptionCallback.onHttpTaskRealException(HttpTask.this, FAILUE,
                                    response.code() + "," + response.message());
                        }
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                    sLog.e(e);
                    onHttpFailed(FAILUE, SYSTEM_ERROR);
                    if (sRealExceptionCallback != null) {
                        sRealExceptionCallback.onHttpTaskRealException(HttpTask.this, FAILUE, e.getMessage());
                    }
                } finally {
                    response.close();
                    long currTimestamp = System.currentTimeMillis();
                    long diff = currTimestamp - mStartTimestamp;
                    if (diff > 2000) {
                        sLog.w(mMethod + "," + formatApiTime(diff));
                    } else {
                        sLog.i(mMethod + "," + formatApiTime(diff));
                    }
                }

            }
        });
        return this;
    }

    private boolean isNetworkError(String message) {
        boolean b = false;
        for (String s : NETWORK_ERROR_CASE_LIST) {
            if (message.contains(s)) {
                b = true;
                break;
            }
        }
        return b;
    }

    private String formatApiTime(long diff) {
        if (diff < 1000) {
            return diff + "ms";
        }
        long sec = diff / 1000;
        long ms = diff % 1000;
        return sec + "s" + ms + "ms";
    }

    public Map<String, Object> getParams() {
        return mParams;
    }

    public Map<String, String> getHeaders() {
        return mHeaders;
    }

    private void onHttpStart() {
        sLog.urlD(getRealUrl(), mParams);
        sHandler.post(new Runnable() {
            @Override
            public void run() {
                setFinished(false);
                if (mWeakReferenceCallback) {
                    CallBack callBack = mWrCallBack.get();
                    if (null != callBack) {
                        callBack.onHttpStart(HttpTask.this);
                    } else {
                        sLog.w("callBack was destroyed");
                    }
                } else {
                    if (mCallBack != null) {
                        mCallBack.onHttpStart(HttpTask.this);
                    }
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
                if (mWeakReferenceCallback) {
                    CallBack callBack = mWrCallBack.get();
                    if (null != callBack) {
                        callBack.onHttpSuccess(HttpTask.this, entity);
                    } else {
                        sLog.w("callBack was destroyed");
                    }
                } else {
                    if (mCallBack != null) {
                        mCallBack.onHttpSuccess(HttpTask.this, entity);
                    }
                }

                if (null != mAfterCallBack) {
                    mAfterCallBack.onSuccess(HttpTask.this, entity, modelStr);
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
                if (mWeakReferenceCallback) {
                    CallBack callBack = mWrCallBack.get();
                    if (null != callBack) {
                        callBack.onHttpFailed(HttpTask.this, errorCode, message);
                    } else {
                        sLog.w("callBack was destroyed");
                    }
                } else {
                    if (null != mCallBack) {
                        mCallBack.onHttpFailed(HttpTask.this, errorCode, message);
                    }
                }
                if (null != mAfterCallBack) {
                    mAfterCallBack.onFailed(HttpTask.this, errorCode, message);
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

    public HttpTask setDefaultFileExtension(String defaultFileExtension) {
        mDefaultFileExtension = defaultFileExtension;
        return this;
    }

    public static void setRealExceptionCallback(RealExceptionCallback realExceptionCallback) {
        sRealExceptionCallback = realExceptionCallback;
    }

    public static void setClientServerTimeDiffCallback(ClientServerTimeDiffCallback clientServerTimeDiffCallback) {
        sClientServerTimeDiffCallback = clientServerTimeDiffCallback;
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

    public boolean getExtraParamBoolean(String key) {
        Object o = getExtraParam(key);
        if (o instanceof Boolean) {
            return (boolean) o;
        }
        L.GL.e("the value for [%s] must be boolean!!!", key);
        return false;
    }

    public long getExtraParamLong(String key) {
        Object o = getExtraParam(key);
        if (o instanceof Long) {
            return (long) o;
        }
        L.GL.e("the value for [%s] must be long!!!", key);
        return -1;
    }

    public int getExtraParamInt(String key) {
        Object o = getExtraParam(key);
        if (o instanceof Integer) {
            return (int) o;
        }
        L.GL.e("the value for [%s] must be int!!!", key);
        return -1;
    }

    public String getExtraParamString(String key) {
        Object o = getExtraParam(key);
        if (o instanceof String) {
            return (String) o;
        }
        L.GL.e("the value for [%s] must be string!!!", key);
        return "";
    }

    public float getExtraParamFloat(String key) {
        Object o = getExtraParam(key);
        if (o instanceof Float) {
            return (Float) o;
        }
        L.GL.e("the value for [%s] must be float!!!", key);
        return -1f;
    }

    public double getExtraParamDouble(String key) {
        Object o = getExtraParam(key);
        if (o instanceof Double) {
            return (double) o;
        }
        L.GL.e("the value for [%s] must be double!!!", key);
        return -1;
    }

    public interface ICommonErrorDeal {
        void onFailed(HttpTask httpTask, int code, String message);
    }

    public interface CallBack {
        void onHttpStart(HttpTask httpTask);

        void onHttpSuccess(HttpTask httpTask, Object entity);

        void onHttpFailed(HttpTask httpTask, int errorCode, String message);
    }

    public interface FlowCallBack {
        void onSuccess(HttpTask httpTask, Object entity, String modelStr);

        void onFailed(HttpTask httpTask, int errorCode, String message);
    }


    public interface IDataConverter {
        Object doConvert(String dataStr, Class responseClass);
    }

    public interface ICommonHeadersAndParameters {

        void init(Context context);

        Map<String, String> getHeaders(String method, Map<String, Object> params);

        Map<String, Object> getParams(String method, Map<String, Object> params);
    }

    public interface IHttpResponse {
        int getCode();

        String getMessage();
    }

    private static void calculateTimeDiff(Response response) {
        String dateStr = response.header("Date");
        try {
            Date date = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz", Locale.US).parse(
                    dateStr);
            sTimeDiff = System.currentTimeMillis() - date.getTime();
            if (sClientServerTimeDiffCallback != null) {
                sClientServerTimeDiffCallback.onClientServerTimeDiff(sTimeDiff);
            }
            sLog.v("local and server time differ [%d]", sTimeDiff);
        } catch (Exception e) {
            sTimeDiff = 0;
            e.printStackTrace();
            sLog.e(e);
        }
    }

    public static long getServerCurrentTimeMillis() {
        return System.currentTimeMillis() - sTimeDiff;
    }

    private String getFileExtensionFromPath(String path) {
        return path.substring(path.lastIndexOf(".") + 1);
    }

    private String getMimeTypeFromExtension(String extension) {
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension);
    }

    private String getImportantMessage(String message) {
        StringBuilder sb = new StringBuilder();
        if (sDebug) {
            sb.append("api:").append(mMethod).append(",");
        }
        sb.append(message == null ? SYSTEM_ERROR : message);
        return sb.toString();
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

    public static class SimpleFlowCallBack implements FlowCallBack {

        @Override
        public void onSuccess(HttpTask httpTask, Object entity, String modelStr) {

        }

        @Override
        public void onFailed(HttpTask httpTask, int errorCode, String message) {

        }
    }


    public interface RealExceptionCallback {
        void onHttpTaskRealException(HttpTask httpTask, int code, String exception);
    }

    public interface ClientServerTimeDiffCallback {
        void onClientServerTimeDiff(long millisecond);
    }

    public static class Param {
        private int mConnectTimeout = 30;
        private int mReadTimeout = 30;
        private int mWriteTimeout = 30;

        public Param setConnectTimeout(int connectTimeout) {
            mConnectTimeout = connectTimeout;
            return this;
        }

        public Param setReadTimeout(int readTimeout) {
            mReadTimeout = readTimeout;
            return this;
        }

        public Param setWriteTimeout(int writeTimeout) {
            mWriteTimeout = writeTimeout;
            return this;
        }
    }
}
