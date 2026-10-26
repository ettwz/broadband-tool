/*
 * This file is auto-generated.  DO NOT MODIFY.
 * Using: build-tools/36.0.0/aidl --lang=java --structured --min_sdk_version=28 --version=10 --hash=7651ded692085e31b57beae196fc3394eb74a559 -I .cache/dns-aidl/aidl -o .cache/dns-aidl/java .cache/dns-aidl/aidl/dev/broadband/toolbox/dnsapi/IDnsResolver.aidl
 *
 * DO NOT CHECK THIS FILE INTO A CODE TREE (e.g. git, etc..).
 * ALWAYS GENERATE THIS FILE FROM UPDATED AIDL COMPILER
 * AS A BUILD INTERMEDIATE ONLY. THIS IS NOT SOURCE CODE.
 */
package dev.broadband.toolbox.dnsapi;
/** @hide */
public interface IDnsResolver extends android.os.IInterface
{
  /**
   * The version of this interface that the caller is built against.
   * This might be different from what {@link #getInterfaceVersion()
   * getInterfaceVersion} returns as that is the version of the interface
   * that the remote object is implementing.
   */
  public static final int VERSION = 10;
  public static final String HASH = "7651ded692085e31b57beae196fc3394eb74a559";
  /** Default implementation for IDnsResolver. */
  public static class Default implements dev.broadband.toolbox.dnsapi.IDnsResolver
  {
    @Override public boolean isAlive() throws android.os.RemoteException
    {
      return false;
    }
    @Override public void registerEventListener(dev.broadband.toolbox.dnsapi.metrics.INetdEventListener listener) throws android.os.RemoteException
    {
    }
    @Override public void setResolverConfiguration(dev.broadband.toolbox.dnsapi.ResolverParamsParcel resolverParams) throws android.os.RemoteException
    {
    }
    @Override public void getResolverInfo(int netId, java.lang.String[] servers, java.lang.String[] domains, java.lang.String[] tlsServers, int[] params, int[] stats, int[] wait_for_pending_req_timeout_count) throws android.os.RemoteException
    {
    }
    @Override public void startPrefix64Discovery(int netId) throws android.os.RemoteException
    {
    }
    @Override public void stopPrefix64Discovery(int netId) throws android.os.RemoteException
    {
    }
    @Override public java.lang.String getPrefix64(int netId) throws android.os.RemoteException
    {
      return null;
    }
    @Override public void createNetworkCache(int netId) throws android.os.RemoteException
    {
    }
    @Override public void destroyNetworkCache(int netId) throws android.os.RemoteException
    {
    }
    @Override public void setLogSeverity(int logSeverity) throws android.os.RemoteException
    {
    }
    @Override public void flushNetworkCache(int netId) throws android.os.RemoteException
    {
    }
    @Override public void setPrefix64(int netId, java.lang.String prefix) throws android.os.RemoteException
    {
    }
    @Override public void registerUnsolicitedEventListener(dev.broadband.toolbox.dnsapi.resolv.aidl.IDnsResolverUnsolicitedEventListener listener) throws android.os.RemoteException
    {
    }
    @Override public void setResolverOptions(int netId, dev.broadband.toolbox.dnsapi.ResolverOptionsParcel optionParams) throws android.os.RemoteException
    {
    }
    @Override
    public int getInterfaceVersion() {
      return 0;
    }
    @Override
    public String getInterfaceHash() {
      return "";
    }
    @Override
    public android.os.IBinder asBinder() {
      return null;
    }
  }
  /** Local-side IPC implementation stub class. */
  public static abstract class Stub extends android.os.Binder implements dev.broadband.toolbox.dnsapi.IDnsResolver
  {
    /** Construct the stub and attach it to the interface. */
    @SuppressWarnings("this-escape")
    public Stub()
    {
      this.attachInterface(this, DESCRIPTOR);
    }
    /**
     * Cast an IBinder object into an dev.broadband.toolbox.dnsapi.IDnsResolver interface,
     * generating a proxy if needed.
     */
    public static dev.broadband.toolbox.dnsapi.IDnsResolver asInterface(android.os.IBinder obj)
    {
      if ((obj==null)) {
        return null;
      }
      android.os.IInterface iin = obj.queryLocalInterface(DESCRIPTOR);
      if (((iin!=null)&&(iin instanceof dev.broadband.toolbox.dnsapi.IDnsResolver))) {
        return ((dev.broadband.toolbox.dnsapi.IDnsResolver)iin);
      }
      return new dev.broadband.toolbox.dnsapi.IDnsResolver.Stub.Proxy(obj);
    }
    @Override public android.os.IBinder asBinder()
    {
      return this;
    }
    @Override public boolean onTransact(int code, android.os.Parcel data, android.os.Parcel reply, int flags) throws android.os.RemoteException
    {
      java.lang.String descriptor = DESCRIPTOR;
      if (code >= android.os.IBinder.FIRST_CALL_TRANSACTION && code <= android.os.IBinder.LAST_CALL_TRANSACTION) {
        data.enforceInterface(descriptor);
      }
      if (code == INTERFACE_TRANSACTION) {
        reply.writeString(descriptor);
        return true;
      }
      else if (code == TRANSACTION_getInterfaceVersion) {
        reply.writeNoException();
        reply.writeInt(getInterfaceVersion());
        return true;
      }
      else if (code == TRANSACTION_getInterfaceHash) {
        reply.writeNoException();
        reply.writeString(getInterfaceHash());
        return true;
      }
      switch (code)
      {
        case TRANSACTION_isAlive:
        {
          boolean _result = this.isAlive();
          reply.writeNoException();
          reply.writeInt(((_result)?(1):(0)));
          break;
        }
        case TRANSACTION_registerEventListener:
        {
          dev.broadband.toolbox.dnsapi.metrics.INetdEventListener _arg0;
          _arg0 = dev.broadband.toolbox.dnsapi.metrics.INetdEventListener.Stub.asInterface(data.readStrongBinder());
          this.registerEventListener(_arg0);
          reply.writeNoException();
          break;
        }
        case TRANSACTION_setResolverConfiguration:
        {
          dev.broadband.toolbox.dnsapi.ResolverParamsParcel _arg0;
          _arg0 = data.readTypedObject(dev.broadband.toolbox.dnsapi.ResolverParamsParcel.CREATOR);
          this.setResolverConfiguration(_arg0);
          reply.writeNoException();
          break;
        }
        case TRANSACTION_getResolverInfo:
        {
          int _arg0;
          _arg0 = data.readInt();
          java.lang.String[] _arg1;
          int _arg1_length = data.readInt();
          if (_arg1_length > 1000000) {
            throw new android.os.BadParcelableException("Array too large: " + _arg1_length);
          } else if (_arg1_length < 0) {
            _arg1 = null;
          } else {
            _arg1 = new java.lang.String[_arg1_length];
          }
          java.lang.String[] _arg2;
          int _arg2_length = data.readInt();
          if (_arg2_length > 1000000) {
            throw new android.os.BadParcelableException("Array too large: " + _arg2_length);
          } else if (_arg2_length < 0) {
            _arg2 = null;
          } else {
            _arg2 = new java.lang.String[_arg2_length];
          }
          java.lang.String[] _arg3;
          int _arg3_length = data.readInt();
          if (_arg3_length > 1000000) {
            throw new android.os.BadParcelableException("Array too large: " + _arg3_length);
          } else if (_arg3_length < 0) {
            _arg3 = null;
          } else {
            _arg3 = new java.lang.String[_arg3_length];
          }
          int[] _arg4;
          int _arg4_length = data.readInt();
          if (_arg4_length > 1000000) {
            throw new android.os.BadParcelableException("Array too large: " + _arg4_length);
          } else if (_arg4_length < 0) {
            _arg4 = null;
          } else {
            _arg4 = new int[_arg4_length];
          }
          int[] _arg5;
          int _arg5_length = data.readInt();
          if (_arg5_length > 1000000) {
            throw new android.os.BadParcelableException("Array too large: " + _arg5_length);
          } else if (_arg5_length < 0) {
            _arg5 = null;
          } else {
            _arg5 = new int[_arg5_length];
          }
          int[] _arg6;
          int _arg6_length = data.readInt();
          if (_arg6_length > 1000000) {
            throw new android.os.BadParcelableException("Array too large: " + _arg6_length);
          } else if (_arg6_length < 0) {
            _arg6 = null;
          } else {
            _arg6 = new int[_arg6_length];
          }
          this.getResolverInfo(_arg0, _arg1, _arg2, _arg3, _arg4, _arg5, _arg6);
          reply.writeNoException();
          reply.writeStringArray(_arg1);
          reply.writeStringArray(_arg2);
          reply.writeStringArray(_arg3);
          reply.writeIntArray(_arg4);
          reply.writeIntArray(_arg5);
          reply.writeIntArray(_arg6);
          break;
        }
        case TRANSACTION_startPrefix64Discovery:
        {
          int _arg0;
          _arg0 = data.readInt();
          this.startPrefix64Discovery(_arg0);
          reply.writeNoException();
          break;
        }
        case TRANSACTION_stopPrefix64Discovery:
        {
          int _arg0;
          _arg0 = data.readInt();
          this.stopPrefix64Discovery(_arg0);
          reply.writeNoException();
          break;
        }
        case TRANSACTION_getPrefix64:
        {
          int _arg0;
          _arg0 = data.readInt();
          java.lang.String _result = this.getPrefix64(_arg0);
          reply.writeNoException();
          reply.writeString(_result);
          break;
        }
        case TRANSACTION_createNetworkCache:
        {
          int _arg0;
          _arg0 = data.readInt();
          this.createNetworkCache(_arg0);
          reply.writeNoException();
          break;
        }
        case TRANSACTION_destroyNetworkCache:
        {
          int _arg0;
          _arg0 = data.readInt();
          this.destroyNetworkCache(_arg0);
          reply.writeNoException();
          break;
        }
        case TRANSACTION_setLogSeverity:
        {
          int _arg0;
          _arg0 = data.readInt();
          this.setLogSeverity(_arg0);
          reply.writeNoException();
          break;
        }
        case TRANSACTION_flushNetworkCache:
        {
          int _arg0;
          _arg0 = data.readInt();
          this.flushNetworkCache(_arg0);
          reply.writeNoException();
          break;
        }
        case TRANSACTION_setPrefix64:
        {
          int _arg0;
          _arg0 = data.readInt();
          java.lang.String _arg1;
          _arg1 = data.readString();
          this.setPrefix64(_arg0, _arg1);
          reply.writeNoException();
          break;
        }
        case TRANSACTION_registerUnsolicitedEventListener:
        {
          dev.broadband.toolbox.dnsapi.resolv.aidl.IDnsResolverUnsolicitedEventListener _arg0;
          _arg0 = dev.broadband.toolbox.dnsapi.resolv.aidl.IDnsResolverUnsolicitedEventListener.Stub.asInterface(data.readStrongBinder());
          this.registerUnsolicitedEventListener(_arg0);
          reply.writeNoException();
          break;
        }
        case TRANSACTION_setResolverOptions:
        {
          int _arg0;
          _arg0 = data.readInt();
          dev.broadband.toolbox.dnsapi.ResolverOptionsParcel _arg1;
          _arg1 = data.readTypedObject(dev.broadband.toolbox.dnsapi.ResolverOptionsParcel.CREATOR);
          this.setResolverOptions(_arg0, _arg1);
          reply.writeNoException();
          break;
        }
        default:
        {
          return super.onTransact(code, data, reply, flags);
        }
      }
      return true;
    }
    private static class Proxy implements dev.broadband.toolbox.dnsapi.IDnsResolver
    {
      private android.os.IBinder mRemote;
      Proxy(android.os.IBinder remote)
      {
        mRemote = remote;
      }
      private int mCachedVersion = -1;
      private String mCachedHash = "-1";
      @Override public android.os.IBinder asBinder()
      {
        return mRemote;
      }
      public java.lang.String getInterfaceDescriptor()
      {
        return DESCRIPTOR;
      }
      @Override public boolean isAlive() throws android.os.RemoteException
      {
        android.os.Parcel _data = android.os.Parcel.obtain();
        android.os.Parcel _reply = android.os.Parcel.obtain();
        boolean _result;
        try {
          _data.writeInterfaceToken(DESCRIPTOR);
          boolean _status = mRemote.transact(Stub.TRANSACTION_isAlive, _data, _reply, 0);
          if (!_status) {
            throw new android.os.RemoteException("Method isAlive is unimplemented.");
          }
          _reply.readException();
          _result = (0!=_reply.readInt());
        }
        finally {
          _reply.recycle();
          _data.recycle();
        }
        return _result;
      }
      @Override public void registerEventListener(dev.broadband.toolbox.dnsapi.metrics.INetdEventListener listener) throws android.os.RemoteException
      {
        android.os.Parcel _data = android.os.Parcel.obtain();
        android.os.Parcel _reply = android.os.Parcel.obtain();
        try {
          _data.writeInterfaceToken(DESCRIPTOR);
          _data.writeStrongInterface(listener);
          boolean _status = mRemote.transact(Stub.TRANSACTION_registerEventListener, _data, _reply, 0);
          if (!_status) {
            throw new android.os.RemoteException("Method registerEventListener is unimplemented.");
          }
          _reply.readException();
        }
        finally {
          _reply.recycle();
          _data.recycle();
        }
      }
      @Override public void setResolverConfiguration(dev.broadband.toolbox.dnsapi.ResolverParamsParcel resolverParams) throws android.os.RemoteException
      {
        android.os.Parcel _data = android.os.Parcel.obtain();
        android.os.Parcel _reply = android.os.Parcel.obtain();
        try {
          _data.writeInterfaceToken(DESCRIPTOR);
          _data.writeTypedObject(resolverParams, 0);
          boolean _status = mRemote.transact(Stub.TRANSACTION_setResolverConfiguration, _data, _reply, 0);
          if (!_status) {
            throw new android.os.RemoteException("Method setResolverConfiguration is unimplemented.");
          }
          _reply.readException();
        }
        finally {
          _reply.recycle();
          _data.recycle();
        }
      }
      @Override public void getResolverInfo(int netId, java.lang.String[] servers, java.lang.String[] domains, java.lang.String[] tlsServers, int[] params, int[] stats, int[] wait_for_pending_req_timeout_count) throws android.os.RemoteException
      {
        android.os.Parcel _data = android.os.Parcel.obtain();
        android.os.Parcel _reply = android.os.Parcel.obtain();
        try {
          _data.writeInterfaceToken(DESCRIPTOR);
          _data.writeInt(netId);
          _data.writeInt(servers.length);
          _data.writeInt(domains.length);
          _data.writeInt(tlsServers.length);
          _data.writeInt(params.length);
          _data.writeInt(stats.length);
          _data.writeInt(wait_for_pending_req_timeout_count.length);
          boolean _status = mRemote.transact(Stub.TRANSACTION_getResolverInfo, _data, _reply, 0);
          if (!_status) {
            throw new android.os.RemoteException("Method getResolverInfo is unimplemented.");
          }
          _reply.readException();
          _reply.readStringArray(servers);
          _reply.readStringArray(domains);
          _reply.readStringArray(tlsServers);
          _reply.readIntArray(params);
          _reply.readIntArray(stats);
          _reply.readIntArray(wait_for_pending_req_timeout_count);
        }
        finally {
          _reply.recycle();
          _data.recycle();
        }
      }
      @Override public void startPrefix64Discovery(int netId) throws android.os.RemoteException
      {
        android.os.Parcel _data = android.os.Parcel.obtain();
        android.os.Parcel _reply = android.os.Parcel.obtain();
        try {
          _data.writeInterfaceToken(DESCRIPTOR);
          _data.writeInt(netId);
          boolean _status = mRemote.transact(Stub.TRANSACTION_startPrefix64Discovery, _data, _reply, 0);
          if (!_status) {
            throw new android.os.RemoteException("Method startPrefix64Discovery is unimplemented.");
          }
          _reply.readException();
        }
        finally {
          _reply.recycle();
          _data.recycle();
        }
      }
      @Override public void stopPrefix64Discovery(int netId) throws android.os.RemoteException
      {
        android.os.Parcel _data = android.os.Parcel.obtain();
        android.os.Parcel _reply = android.os.Parcel.obtain();
        try {
          _data.writeInterfaceToken(DESCRIPTOR);
          _data.writeInt(netId);
          boolean _status = mRemote.transact(Stub.TRANSACTION_stopPrefix64Discovery, _data, _reply, 0);
          if (!_status) {
            throw new android.os.RemoteException("Method stopPrefix64Discovery is unimplemented.");
          }
          _reply.readException();
        }
        finally {
          _reply.recycle();
          _data.recycle();
        }
      }
      @Override public java.lang.String getPrefix64(int netId) throws android.os.RemoteException
      {
        android.os.Parcel _data = android.os.Parcel.obtain();
        android.os.Parcel _reply = android.os.Parcel.obtain();
        java.lang.String _result;
        try {
          _data.writeInterfaceToken(DESCRIPTOR);
          _data.writeInt(netId);
          boolean _status = mRemote.transact(Stub.TRANSACTION_getPrefix64, _data, _reply, 0);
          if (!_status) {
            throw new android.os.RemoteException("Method getPrefix64 is unimplemented.");
          }
          _reply.readException();
          _result = _reply.readString();
        }
        finally {
          _reply.recycle();
          _data.recycle();
        }
        return _result;
      }
      @Override public void createNetworkCache(int netId) throws android.os.RemoteException
      {
        android.os.Parcel _data = android.os.Parcel.obtain();
        android.os.Parcel _reply = android.os.Parcel.obtain();
        try {
          _data.writeInterfaceToken(DESCRIPTOR);
          _data.writeInt(netId);
          boolean _status = mRemote.transact(Stub.TRANSACTION_createNetworkCache, _data, _reply, 0);
          if (!_status) {
            throw new android.os.RemoteException("Method createNetworkCache is unimplemented.");
          }
          _reply.readException();
        }
        finally {
          _reply.recycle();
          _data.recycle();
        }
      }
      @Override public void destroyNetworkCache(int netId) throws android.os.RemoteException
      {
        android.os.Parcel _data = android.os.Parcel.obtain();
        android.os.Parcel _reply = android.os.Parcel.obtain();
        try {
          _data.writeInterfaceToken(DESCRIPTOR);
          _data.writeInt(netId);
          boolean _status = mRemote.transact(Stub.TRANSACTION_destroyNetworkCache, _data, _reply, 0);
          if (!_status) {
            throw new android.os.RemoteException("Method destroyNetworkCache is unimplemented.");
          }
          _reply.readException();
        }
        finally {
          _reply.recycle();
          _data.recycle();
        }
      }
      @Override public void setLogSeverity(int logSeverity) throws android.os.RemoteException
      {
        android.os.Parcel _data = android.os.Parcel.obtain();
        android.os.Parcel _reply = android.os.Parcel.obtain();
        try {
          _data.writeInterfaceToken(DESCRIPTOR);
          _data.writeInt(logSeverity);
          boolean _status = mRemote.transact(Stub.TRANSACTION_setLogSeverity, _data, _reply, 0);
          if (!_status) {
            throw new android.os.RemoteException("Method setLogSeverity is unimplemented.");
          }
          _reply.readException();
        }
        finally {
          _reply.recycle();
          _data.recycle();
        }
      }
      @Override public void flushNetworkCache(int netId) throws android.os.RemoteException
      {
        android.os.Parcel _data = android.os.Parcel.obtain();
        android.os.Parcel _reply = android.os.Parcel.obtain();
        try {
          _data.writeInterfaceToken(DESCRIPTOR);
          _data.writeInt(netId);
          boolean _status = mRemote.transact(Stub.TRANSACTION_flushNetworkCache, _data, _reply, 0);
          if (!_status) {
            throw new android.os.RemoteException("Method flushNetworkCache is unimplemented.");
          }
          _reply.readException();
        }
        finally {
          _reply.recycle();
          _data.recycle();
        }
      }
      @Override public void setPrefix64(int netId, java.lang.String prefix) throws android.os.RemoteException
      {
        android.os.Parcel _data = android.os.Parcel.obtain();
        android.os.Parcel _reply = android.os.Parcel.obtain();
        try {
          _data.writeInterfaceToken(DESCRIPTOR);
          _data.writeInt(netId);
          _data.writeString(prefix);
          boolean _status = mRemote.transact(Stub.TRANSACTION_setPrefix64, _data, _reply, 0);
          if (!_status) {
            throw new android.os.RemoteException("Method setPrefix64 is unimplemented.");
          }
          _reply.readException();
        }
        finally {
          _reply.recycle();
          _data.recycle();
        }
      }
      @Override public void registerUnsolicitedEventListener(dev.broadband.toolbox.dnsapi.resolv.aidl.IDnsResolverUnsolicitedEventListener listener) throws android.os.RemoteException
      {
        android.os.Parcel _data = android.os.Parcel.obtain();
        android.os.Parcel _reply = android.os.Parcel.obtain();
        try {
          _data.writeInterfaceToken(DESCRIPTOR);
          _data.writeStrongInterface(listener);
          boolean _status = mRemote.transact(Stub.TRANSACTION_registerUnsolicitedEventListener, _data, _reply, 0);
          if (!_status) {
            throw new android.os.RemoteException("Method registerUnsolicitedEventListener is unimplemented.");
          }
          _reply.readException();
        }
        finally {
          _reply.recycle();
          _data.recycle();
        }
      }
      @Override public void setResolverOptions(int netId, dev.broadband.toolbox.dnsapi.ResolverOptionsParcel optionParams) throws android.os.RemoteException
      {
        android.os.Parcel _data = android.os.Parcel.obtain();
        android.os.Parcel _reply = android.os.Parcel.obtain();
        try {
          _data.writeInterfaceToken(DESCRIPTOR);
          _data.writeInt(netId);
          _data.writeTypedObject(optionParams, 0);
          boolean _status = mRemote.transact(Stub.TRANSACTION_setResolverOptions, _data, _reply, 0);
          if (!_status) {
            throw new android.os.RemoteException("Method setResolverOptions is unimplemented.");
          }
          _reply.readException();
        }
        finally {
          _reply.recycle();
          _data.recycle();
        }
      }
      @Override
      public int getInterfaceVersion() throws android.os.RemoteException {
        if (mCachedVersion == -1) {
          android.os.Parcel data = android.os.Parcel.obtain();
          android.os.Parcel reply = android.os.Parcel.obtain();
          try {
            data.writeInterfaceToken(DESCRIPTOR);
            boolean _status = mRemote.transact(Stub.TRANSACTION_getInterfaceVersion, data, reply, 0);
            reply.readException();
            mCachedVersion = reply.readInt();
          } finally {
            reply.recycle();
            data.recycle();
          }
        }
        return mCachedVersion;
      }
      @Override
      public synchronized String getInterfaceHash() throws android.os.RemoteException {
        if ("-1".equals(mCachedHash)) {
          android.os.Parcel data = android.os.Parcel.obtain();
          android.os.Parcel reply = android.os.Parcel.obtain();
          try {
            data.writeInterfaceToken(DESCRIPTOR);
            boolean _status = mRemote.transact(Stub.TRANSACTION_getInterfaceHash, data, reply, 0);
            reply.readException();
            mCachedHash = reply.readString();
          } finally {
            reply.recycle();
            data.recycle();
          }
        }
        return mCachedHash;
      }
    }
    static final int TRANSACTION_isAlive = (android.os.IBinder.FIRST_CALL_TRANSACTION + 0);
    static final int TRANSACTION_registerEventListener = (android.os.IBinder.FIRST_CALL_TRANSACTION + 1);
    static final int TRANSACTION_setResolverConfiguration = (android.os.IBinder.FIRST_CALL_TRANSACTION + 2);
    static final int TRANSACTION_getResolverInfo = (android.os.IBinder.FIRST_CALL_TRANSACTION + 3);
    static final int TRANSACTION_startPrefix64Discovery = (android.os.IBinder.FIRST_CALL_TRANSACTION + 4);
    static final int TRANSACTION_stopPrefix64Discovery = (android.os.IBinder.FIRST_CALL_TRANSACTION + 5);
    static final int TRANSACTION_getPrefix64 = (android.os.IBinder.FIRST_CALL_TRANSACTION + 6);
    static final int TRANSACTION_createNetworkCache = (android.os.IBinder.FIRST_CALL_TRANSACTION + 7);
    static final int TRANSACTION_destroyNetworkCache = (android.os.IBinder.FIRST_CALL_TRANSACTION + 8);
    static final int TRANSACTION_setLogSeverity = (android.os.IBinder.FIRST_CALL_TRANSACTION + 9);
    static final int TRANSACTION_flushNetworkCache = (android.os.IBinder.FIRST_CALL_TRANSACTION + 10);
    static final int TRANSACTION_setPrefix64 = (android.os.IBinder.FIRST_CALL_TRANSACTION + 11);
    static final int TRANSACTION_registerUnsolicitedEventListener = (android.os.IBinder.FIRST_CALL_TRANSACTION + 12);
    static final int TRANSACTION_setResolverOptions = (android.os.IBinder.FIRST_CALL_TRANSACTION + 13);
    static final int TRANSACTION_getInterfaceVersion = (android.os.IBinder.FIRST_CALL_TRANSACTION + 16777214);
    static final int TRANSACTION_getInterfaceHash = (android.os.IBinder.FIRST_CALL_TRANSACTION + 16777213);
  }
  /** @hide */
  public static final java.lang.String DESCRIPTOR = "android$net$IDnsResolver".replace('$', '.');
  public static final int RESOLVER_PARAMS_SAMPLE_VALIDITY = 0;
  public static final int RESOLVER_PARAMS_SUCCESS_THRESHOLD = 1;
  public static final int RESOLVER_PARAMS_MIN_SAMPLES = 2;
  public static final int RESOLVER_PARAMS_MAX_SAMPLES = 3;
  public static final int RESOLVER_PARAMS_BASE_TIMEOUT_MSEC = 4;
  public static final int RESOLVER_PARAMS_RETRY_COUNT = 5;
  public static final int RESOLVER_PARAMS_COUNT = 6;
  public static final int RESOLVER_STATS_SUCCESSES = 0;
  public static final int RESOLVER_STATS_ERRORS = 1;
  public static final int RESOLVER_STATS_TIMEOUTS = 2;
  public static final int RESOLVER_STATS_INTERNAL_ERRORS = 3;
  public static final int RESOLVER_STATS_RTT_AVG = 4;
  public static final int RESOLVER_STATS_LAST_SAMPLE_TIME = 5;
  public static final int RESOLVER_STATS_USABLE = 6;
  public static final int RESOLVER_STATS_COUNT = 7;
  public static final int DNS_RESOLVER_LOG_VERBOSE = 0;
  public static final int DNS_RESOLVER_LOG_DEBUG = 1;
  public static final int DNS_RESOLVER_LOG_INFO = 2;
  public static final int DNS_RESOLVER_LOG_WARNING = 3;
  public static final int DNS_RESOLVER_LOG_ERROR = 4;
  public static final int TC_MODE_DEFAULT = 0;
  public static final int TC_MODE_UDP_TCP = 1;
  public static final int TRANSPORT_UNKNOWN = -1;
  public static final int TRANSPORT_CELLULAR = 0;
  public static final int TRANSPORT_WIFI = 1;
  public static final int TRANSPORT_BLUETOOTH = 2;
  public static final int TRANSPORT_ETHERNET = 3;
  public static final int TRANSPORT_VPN = 4;
  public static final int TRANSPORT_WIFI_AWARE = 5;
  public static final int TRANSPORT_LOWPAN = 6;
  public static final int TRANSPORT_TEST = 7;
  public static final int TRANSPORT_USB = 8;
  public boolean isAlive() throws android.os.RemoteException;
  public void registerEventListener(dev.broadband.toolbox.dnsapi.metrics.INetdEventListener listener) throws android.os.RemoteException;
  public void setResolverConfiguration(dev.broadband.toolbox.dnsapi.ResolverParamsParcel resolverParams) throws android.os.RemoteException;
  public void getResolverInfo(int netId, java.lang.String[] servers, java.lang.String[] domains, java.lang.String[] tlsServers, int[] params, int[] stats, int[] wait_for_pending_req_timeout_count) throws android.os.RemoteException;
  public void startPrefix64Discovery(int netId) throws android.os.RemoteException;
  public void stopPrefix64Discovery(int netId) throws android.os.RemoteException;
  public java.lang.String getPrefix64(int netId) throws android.os.RemoteException;
  public void createNetworkCache(int netId) throws android.os.RemoteException;
  public void destroyNetworkCache(int netId) throws android.os.RemoteException;
  public void setLogSeverity(int logSeverity) throws android.os.RemoteException;
  public void flushNetworkCache(int netId) throws android.os.RemoteException;
  public void setPrefix64(int netId, java.lang.String prefix) throws android.os.RemoteException;
  public void registerUnsolicitedEventListener(dev.broadband.toolbox.dnsapi.resolv.aidl.IDnsResolverUnsolicitedEventListener listener) throws android.os.RemoteException;
  public void setResolverOptions(int netId, dev.broadband.toolbox.dnsapi.ResolverOptionsParcel optionParams) throws android.os.RemoteException;
  public int getInterfaceVersion() throws android.os.RemoteException;
  public String getInterfaceHash() throws android.os.RemoteException;
}
