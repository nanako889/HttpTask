# HttpTask

    A okhttp tool    

# Download

    implementation 'top.qinbaowei:httptask:3.1' 

# Proguard

    ##---------------Begin: proguard configuration for Gson  ----------
    # Gson uses generic type information stored in a class file when working with fields. Proguard
    # removes such information by default, so configure it to keep all of it.
    -keepattributes Signature
    
    # For using GSON @Expose annotation
    -keepattributes *Annotation*
    
    # Gson specific classes
    -keep class sun.misc.Unsafe { *; }
    #-keep class com.google.gson.stream.** { *; }
    # Application classes that will be serialized/deserialized over Gson
    ##---------------End: proguard configuration for Gson  ----------
    
    ##---------------Begin: proguard configuration for OkHttp  ----------
    # OkHttp
    #-dontwarn okio.**
    #-keepattributes Signature
    #-keepattributes *Annotation*
    #-keep class com.squareup.okhttp.** { *; }
    #-keep interface com.squareup.okhttp.** { *; }
    #-dontwarn com.squareup.okhttp.**
    
    -dontwarn okhttp3.**
    -dontwarn okio.**
    -dontwarn javax.annotation.**
    -dontwarn org.conscrypt.**
    # A resource is loaded with a relative path so the package of this class must be preserved.
    -keepnames class okhttp3.internal.publicsuffix.PublicSuffixDatabase
    -keep class com.http.okhttp.HttpTask$HttpResponse{*;}
    -keep class com.http.okhttp.HttpTask$HttpResponse$Sub{*;}
    ##---------------End: proguard configuration for OkHttp  ----------
    
