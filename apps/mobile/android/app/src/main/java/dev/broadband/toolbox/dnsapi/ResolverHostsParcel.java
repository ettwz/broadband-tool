/*
 * This file is auto-generated.  DO NOT MODIFY.
 * Using: build-tools/36.0.0/aidl --lang=java --structured --min_sdk_version=28 --version=10 --hash=7651ded692085e31b57beae196fc3394eb74a559 -I .cache/dns-aidl/aidl -o .cache/dns-aidl/java .cache/dns-aidl/aidl/dev/broadband/toolbox/dnsapi/ResolverHostsParcel.aidl
 *
 * DO NOT CHECK THIS FILE INTO A CODE TREE (e.g. git, etc..).
 * ALWAYS GENERATE THIS FILE FROM UPDATED AIDL COMPILER
 * AS A BUILD INTERMEDIATE ONLY. THIS IS NOT SOURCE CODE.
 */
package dev.broadband.toolbox.dnsapi;
/** @hide */
public class ResolverHostsParcel implements android.os.Parcelable
{
  public java.lang.String ipAddr;
  public java.lang.String hostName = "";
  public static final android.os.Parcelable.Creator<ResolverHostsParcel> CREATOR = new android.os.Parcelable.Creator<ResolverHostsParcel>() {
    @Override
    public ResolverHostsParcel createFromParcel(android.os.Parcel _aidl_source) {
      ResolverHostsParcel _aidl_out = new ResolverHostsParcel();
      _aidl_out.readFromParcel(_aidl_source);
      return _aidl_out;
    }
    @Override
    public ResolverHostsParcel[] newArray(int _aidl_size) {
      return new ResolverHostsParcel[_aidl_size];
    }
  };
  @Override public final void writeToParcel(android.os.Parcel _aidl_parcel, int _aidl_flag)
  {
    int _aidl_start_pos = _aidl_parcel.dataPosition();
    _aidl_parcel.writeInt(0);
    _aidl_parcel.writeString(ipAddr);
    _aidl_parcel.writeString(hostName);
    int _aidl_end_pos = _aidl_parcel.dataPosition();
    _aidl_parcel.setDataPosition(_aidl_start_pos);
    _aidl_parcel.writeInt(_aidl_end_pos - _aidl_start_pos);
    _aidl_parcel.setDataPosition(_aidl_end_pos);
  }
  public final void readFromParcel(android.os.Parcel _aidl_parcel)
  {
    int _aidl_start_pos = _aidl_parcel.dataPosition();
    int _aidl_parcelable_size = _aidl_parcel.readInt();
    try {
      if (_aidl_parcelable_size < 4) throw new android.os.BadParcelableException("Parcelable too small");;
      if (_aidl_parcel.dataPosition() - _aidl_start_pos >= _aidl_parcelable_size) return;
      ipAddr = _aidl_parcel.readString();
      if (_aidl_parcel.dataPosition() - _aidl_start_pos >= _aidl_parcelable_size) return;
      hostName = _aidl_parcel.readString();
    } finally {
      if (_aidl_start_pos > (Integer.MAX_VALUE - _aidl_parcelable_size)) {
        throw new android.os.BadParcelableException("Overflow in the size of parcelable");
      }
      _aidl_parcel.setDataPosition(_aidl_start_pos + _aidl_parcelable_size);
    }
  }
  @Override
  public int describeContents() {
    int _mask = 0;
    return _mask;
  }
}
