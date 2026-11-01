#!/usr/bin/env python3
"""graft_sig.py target.apk donor.apk out.apk
Copies the APK signing block (v2/v3) + META-INF from a platform-signed donor APK (e.g. MtkSystemUI.apk)
into target.apk. Works only on firmware that does not verify the digests of /system apps."""
import sys, io, struct, zipfile
t, dn, o = sys.argv[1:4]
dd = open(dn, 'rb').read()
e = dd.rindex(b'PK\x05\x06'); cd_off = struct.unpack_from('<I', dd, e + 16)[0]
assert dd[cd_off-16:cd_off] == b'APK Sig Block 42', 'donor has no signing block'
sz = struct.unpack_from('<Q', dd, cd_off - 24)[0]
block = dd[cd_off - sz - 8:cd_off]
tz = zipfile.ZipFile(t); dz = zipfile.ZipFile(dn)
items = [(zi, tz.read(zi.filename)) for zi in tz.infolist() if not zi.filename.startswith('META-INF/')]
items += [(zi, dz.read(zi.filename)) for zi in dz.infolist() if zi.filename.startswith('META-INF/')]
buf = io.BytesIO(); zo = zipfile.ZipFile(buf, 'w')
for zi, data in items:
    ni = zipfile.ZipInfo(zi.filename, zi.date_time)
    ni.compress_type = zi.compress_type; ni.external_attr = zi.external_attr; ni.create_system = zi.create_system
    if ni.compress_type == zipfile.ZIP_STORED:  # 4-byte alignment for stored entries
        off = buf.tell() + 30 + len(zi.filename.encode()); pad = (-off) % 4
        if pad: ni.extra = b'\x00' * pad
    zo.writestr(ni, data)
zo.close(); d = bytearray(buf.getvalue())
e = d.rindex(b'PK\x05\x06'); cd = struct.unpack_from('<I', d, e + 16)[0]
out = bytearray(bytes(d[:cd]) + block + bytes(d[cd:]))
e2 = bytes(out).rindex(b'PK\x05\x06'); struct.pack_into('<I', out, e2 + 16, cd + len(block))
open(o, 'wb').write(out)
print('ok', len(out), 'testzip', zipfile.ZipFile(o).testzip())
