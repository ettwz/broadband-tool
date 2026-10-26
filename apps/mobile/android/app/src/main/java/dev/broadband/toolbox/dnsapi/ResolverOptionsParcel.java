/*
 * This file is auto-generated.  DO NOT MODIFY.
 * Using: build-tools/36.0.0/aidl --lang=java --structured --min_sdk_version=28 --version=10 --hash=7651ded692085e31b57beae196fc3394eb74a559 -I .cache/dns-aidl/aidl -o .cache/dns-aidl/java .cache/dns-aidl/aidl/dev/broadband/toolbox/dnsapi/ResolverOptionsParcel.aidl
 *
 * DO NOT CHECK THIS FILE INTO A CODE TREE (e.g. git, etc..).
 * ALWAYS GENERATE THIS FILE FROM UPDATED AIDL COMPILER
 * AS A BUILD INTERMEDIATE ONLY. THIS IS NOT SOURCE CODE.
 */
package dev.broadband.toolbox.dnsapi;
/** @hide */
public class ResolverOptionsParcel implements android.os.Parcelable
{
  public dev.broadband.toolbox.dnsapi.ResolverHostsParcel[] hosts = {};
  public int tcMode = 0;
  public boolean enforceDnsUid = false;
  public static final android.os.Parcelable.Creator<ResolverOptionsParcel> CREATOR = new android.os.Parcelable.Creator<ResolverOptionsParcel>() {
    @Override
    public ResolverOptionsParcel createFromParcel(android.os.Parcel _aidl_source) {
      ResolverOptionsParcel _aidl_out = new ResolverOptionsParcel();
      _aidl_out.readFromParcel(_aidl_source);
      return _aidl_out;
    }
    @Override
    public ResolverOptionsParcel[] newArray(int _aidl_size) {
      return new ResolverOptionsParcel[_aidl_size];
    }
  };
  @Override public final void writeToParcel(android.os.Parcel _aidl_parcel, int _aidl_flag)
  {
    int _aidl_start_pos = _aidl_parcel.dataPosition();
    _aidl_parcel.writeInt(0);
    _aidl_parcel.writeTypedArray(hosts, _aidl_flag);
    _aidl_parcel.writeInt(tcMode);
    _aidl_parcel.writeInt(((enforceDnsUid)?(1):(0)));
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
      hosts = _aidl_parcel.createTypedArray(dev.broadband.toolbox.dnsapi.ResolverHostsParcel.CREATOR);
      if (_aidl_parcel.dataPosition() - _aidl_start_pos >= _aidl_parcelable_size) return;
      tcMode = _aidl_parcel.readInt();
      if (_aidl_parcel.dataPosition() - _aidl_start_pos >= _aidl_parcelable_size) return;
      enforceDnsUid = (0!=_aidl_parcel.readInt());
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
    _mask |= describeContents(hosts);
    return _mask;
  }
  private int describeContents(Object _v) {
    if (_v == null) return 0;
    if (_v instanceof Object[]) {
      int _mask = 0;
      for (Object o : (Object[]) _v) {
        _mask |= describeContents(o);
      }
      return _mask;
    }
    if (_v instanceof android.os.Parcelable) {
      return ((android.os.Parcelable) _v).describeContents();
    }
    return 0;
  }
}
