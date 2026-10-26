/*
 * This file is auto-generated.  DO NOT MODIFY.
 * Using: build-tools/36.0.0/aidl --lang=java --structured --min_sdk_version=28 --version=10 --hash=7651ded692085e31b57beae196fc3394eb74a559 -I .cache/dns-aidl/aidl -o .cache/dns-aidl/java .cache/dns-aidl/aidl/dev/broadband/toolbox/dnsapi/resolv/aidl/IDnsResolverUnsolicitedEventListener.aidl
 *
 * DO NOT CHECK THIS FILE INTO A CODE TREE (e.g. git, etc..).
 * ALWAYS GENERATE THIS FILE FROM UPDATED AIDL COMPILER
 * AS A BUILD INTERMEDIATE ONLY. THIS IS NOT SOURCE CODE.
 */
package dev.broadband.toolbox.dnsapi.resolv.aidl;
/** @hide */
public interface IDnsResolverUnsolicitedEventListener extends android.os.IInterface
{
  /**
   * The version of this interface that the caller is built against.
   * This might be different from what {@link #getInterfaceVersion()
   * getInterfaceVersion} returns as that is the version of the interface
   * that the remote object is implementing.
   */
  public static final int VERSION = 10;
  public static final String HASH = "7651ded692085e31b57beae196fc3394eb74a559";
  /** Default implementation for IDnsResolverUnsolicitedEventListener. */
  public static class Default implements dev.broadband.toolbox.dnsapi.resolv.aidl.IDnsResolverUnsolicitedEventListener
  {
    @Override public void onDnsHealthEvent(dev.broadband.toolbox.dnsapi.resolv.aidl.DnsHealthEventParcel dnsHealthEvent) throws android.os.RemoteException
    {
    }
    @Override public void onNat64PrefixEvent(dev.broadband.toolbox.dnsapi.resolv.aidl.Nat64PrefixEventParcel nat64PrefixEvent) throws android.os.RemoteException
    {
    }
    @Override public void onPrivateDnsValidationEvent(dev.broadband.toolbox.dnsapi.resolv.aidl.PrivateDnsValidationEventParcel privateDnsValidationEvent) throws android.os.RemoteException
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
  public static abstract class Stub extends android.os.Binder implements dev.broadband.toolbox.dnsapi.resolv.aidl.IDnsResolverUnsolicitedEventListener
  {
    /** Construct the stub and attach it to the interface. */
    @SuppressWarnings("this-escape")
    public Stub()
    {
      this.attachInterface(this, DESCRIPTOR);
    }
    /**
     * Cast an IBinder object into an dev.broadband.toolbox.dnsapi.resolv.aidl.IDnsResolverUnsolicitedEventListener interface,
     * generating a proxy if needed.
     */
    public static dev.broadband.toolbox.dnsapi.resolv.aidl.IDnsResolverUnsolicitedEventListener asInterface(android.os.IBinder obj)
    {
      if ((obj==null)) {
        return null;
      }
      android.os.IInterface iin = obj.queryLocalInterface(DESCRIPTOR);
      if (((iin!=null)&&(iin instanceof dev.broadband.toolbox.dnsapi.resolv.aidl.IDnsResolverUnsolicitedEventListener))) {
        return ((dev.broadband.toolbox.dnsapi.resolv.aidl.IDnsResolverUnsolicitedEventListener)iin);
      }
      return new dev.broadband.toolbox.dnsapi.resolv.aidl.IDnsResolverUnsolicitedEventListener.Stub.Proxy(obj);
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
        case TRANSACTION_onDnsHealthEvent:
        {
          dev.broadband.toolbox.dnsapi.resolv.aidl.DnsHealthEventParcel _arg0;
          _arg0 = data.readTypedObject(dev.broadband.toolbox.dnsapi.resolv.aidl.DnsHealthEventParcel.CREATOR);
          this.onDnsHealthEvent(_arg0);
          break;
        }
        case TRANSACTION_onNat64PrefixEvent:
        {
          dev.broadband.toolbox.dnsapi.resolv.aidl.Nat64PrefixEventParcel _arg0;
          _arg0 = data.readTypedObject(dev.broadband.toolbox.dnsapi.resolv.aidl.Nat64PrefixEventParcel.CREATOR);
          this.onNat64PrefixEvent(_arg0);
          break;
        }
        case TRANSACTION_onPrivateDnsValidationEvent:
        {
          dev.broadband.toolbox.dnsapi.resolv.aidl.PrivateDnsValidationEventParcel _arg0;
          _arg0 = data.readTypedObject(dev.broadband.toolbox.dnsapi.resolv.aidl.PrivateDnsValidationEventParcel.CREATOR);
          this.onPrivateDnsValidationEvent(_arg0);
          break;
        }
        default:
        {
          return super.onTransact(code, data, reply, flags);
        }
      }
      return true;
    }
    private static class Proxy implements dev.broadband.toolbox.dnsapi.resolv.aidl.IDnsResolverUnsolicitedEventListener
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
      @Override public void onDnsHealthEvent(dev.broadband.toolbox.dnsapi.resolv.aidl.DnsHealthEventParcel dnsHealthEvent) throws android.os.RemoteException
      {
        android.os.Parcel _data = android.os.Parcel.obtain();
        try {
          _data.writeInterfaceToken(DESCRIPTOR);
          _data.writeTypedObject(dnsHealthEvent, 0);
          boolean _status = mRemote.transact(Stub.TRANSACTION_onDnsHealthEvent, _data, null, android.os.IBinder.FLAG_ONEWAY);
          if (!_status) {
            throw new android.os.RemoteException("Method onDnsHealthEvent is unimplemented.");
          }
        }
        finally {
          _data.recycle();
        }
      }
      @Override public void onNat64PrefixEvent(dev.broadband.toolbox.dnsapi.resolv.aidl.Nat64PrefixEventParcel nat64PrefixEvent) throws android.os.RemoteException
      {
        android.os.Parcel _data = android.os.Parcel.obtain();
        try {
          _data.writeInterfaceToken(DESCRIPTOR);
          _data.writeTypedObject(nat64PrefixEvent, 0);
          boolean _status = mRemote.transact(Stub.TRANSACTION_onNat64PrefixEvent, _data, null, android.os.IBinder.FLAG_ONEWAY);
          if (!_status) {
            throw new android.os.RemoteException("Method onNat64PrefixEvent is unimplemented.");
          }
        }
        finally {
          _data.recycle();
        }
      }
      @Override public void onPrivateDnsValidationEvent(dev.broadband.toolbox.dnsapi.resolv.aidl.PrivateDnsValidationEventParcel privateDnsValidationEvent) throws android.os.RemoteException
      {
        android.os.Parcel _data = android.os.Parcel.obtain();
        try {
          _data.writeInterfaceToken(DESCRIPTOR);
          _data.writeTypedObject(privateDnsValidationEvent, 0);
          boolean _status = mRemote.transact(Stub.TRANSACTION_onPrivateDnsValidationEvent, _data, null, android.os.IBinder.FLAG_ONEWAY);
          if (!_status) {
            throw new android.os.RemoteException("Method onPrivateDnsValidationEvent is unimplemented.");
          }
        }
        finally {
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
    static final int TRANSACTION_onDnsHealthEvent = (android.os.IBinder.FIRST_CALL_TRANSACTION + 0);
    static final int TRANSACTION_onNat64PrefixEvent = (android.os.IBinder.FIRST_CALL_TRANSACTION + 1);
    static final int TRANSACTION_onPrivateDnsValidationEvent = (android.os.IBinder.FIRST_CALL_TRANSACTION + 2);
    static final int TRANSACTION_getInterfaceVersion = (android.os.IBinder.FIRST_CALL_TRANSACTION + 16777214);
    static final int TRANSACTION_getInterfaceHash = (android.os.IBinder.FIRST_CALL_TRANSACTION + 16777213);
  }
  /** @hide */
  public static final java.lang.String DESCRIPTOR = "android$net$resolv$aidl$IDnsResolverUnsolicitedEventListener".replace('$', '.');
  public static final int DNS_HEALTH_RESULT_OK = 0;
  public static final int DNS_HEALTH_RESULT_TIMEOUT = 255;
  public static final int PREFIX_OPERATION_ADDED = 1;
  public static final int PREFIX_OPERATION_REMOVED = 2;
  public static final int VALIDATION_RESULT_SUCCESS = 1;
  public static final int VALIDATION_RESULT_FAILURE = 2;
  public static final int PROTOCOL_DOT = 1;
  public static final int PROTOCOL_DOH = 2;
  public void onDnsHealthEvent(dev.broadband.toolbox.dnsapi.resolv.aidl.DnsHealthEventParcel dnsHealthEvent) throws android.os.RemoteException;
  public void onNat64PrefixEvent(dev.broadband.toolbox.dnsapi.resolv.aidl.Nat64PrefixEventParcel nat64PrefixEvent) throws android.os.RemoteException;
  public void onPrivateDnsValidationEvent(dev.broadband.toolbox.dnsapi.resolv.aidl.PrivateDnsValidationEventParcel privateDnsValidationEvent) throws android.os.RemoteException;
  public int getInterfaceVersion() throws android.os.RemoteException;
  public String getInterfaceHash() throws android.os.RemoteException;
}
