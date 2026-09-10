# OPPO Reno5: pehla mic-routing test

Target: CPH2159, Android 13, ColorOS 13.0, build CPH2159_11_F.43(B100P01), Audionic Battlebuds. Yeh diagnostic APK hai; universal microphone switcher abhi prove nahi hua.

## Install aur pehla test

1. `artifacts/mic-route-test-debug.apk` phone par download/open karke install karein. Installer agar poochay to is source se installation allow karein.
2. Battlebuds connect karein. **Mic Route Test** khol kar **Allow microphone & Bluetooth** press karein.
3. **Phone mic** select karein. Agar ek se zyada phone inputs hain to pehla select karein aur report mein uska number note karein.
4. **Record 10-second test** press karein. Earbuds aur phone ko door rakh kar pehle phone ke qareeb bolain “phone”, phir earbuds ke qareeb “earbuds”. **Actual test mic** aur MATCH/MISMATCH/UNVERIFIED dekhein. Level akela mic identity ka proof nahi hai.
5. **Listen to last test on earbuds** se Bluetooth media output choose karein. Normal, comfortable media volume rakhein. Recording phone par save nahi hoti; next test/reset/process exit par discard hoti hai.
6. Bluetooth input available ho to usay select karke repeat karein. Input list mein na ho to **Request communication output…** mein Bluetooth call audio choose karke refresh karein. Yeh output aur matching input dono badal sakta hai.
7. Recording ke beech selected input change karein. Request accept hona success nahi: actual route aur sunai dene wali recording dono check karein.

AudioTrack playback muted start hoti hai jab tak requested output observe na ho. Route mismatch/disconnect par playback stop hoti hai. Android device changes asynchronous hain; is test mein non-sensitive sample words hi use karein.

## WhatsApp aur SIM test

Apni app ki recording mein MATCH ka matlab sirf apni recording hai. Isay WhatsApp/SIM success na samjhein.

1. **Stop audio** press karke recording-stopped log ka wait karein. Earbuds connected rakhain.
2. Pehle bina communication request ke WhatsApp voice note, WhatsApp call aur SIM call ka baseline check karein. Calls mein test partner se mic quality/identity confirm karwayein. Voice note playback bhi earbuds mein check karein.
3. Wapas test app mein **Phone mic** select karein, phir target app mein wahi activity repeat karein. Yeh jaan-boojh kar check karta hai ke local selection doosri app tak jati bhi hai ya nahi; normal APIs se expected global control nahi hai.
4. **Request communication output…** se Bluetooth choose karein, phir 2 minutes ke andar target app test karein. Separate test mein phone earpiece/speaker request karein aur note karein ke input ke saath output bhi badalti hai ya nahi. Speaker choose karne se audio phone par aa sakti hai.
5. Mid-call test ke liye call se test app par wapas aayein; recording start kiye baghair communication output request karein; call par wapas ja kar mic aur output verify karein. Test app `MODE_IN_COMMUNICATION` force nahi karti. Calling app request override kar sakti hai.
6. Har case ke baad **Add manual test result** mein app/activity aur evidence save karein. PASS tabhi jab actual phone mic **aur** earbuds output dono confirm hon. Pata na chale to UNVERIFIED.
7. **Reset routing & clear test audio** press karein. Yeh sirf hamari request clear karta hai; doosri app ki routing control nahi karta.
8. **Export diagnostic report** press karein aur `.txt` file share karein. Report mein audio nahi hoti; device names, build, route events aur aapke notes hotay hain.

Communication request 2 minutes baad, Reset par, ya activity destroy hone par clear hoti hai. Background mein Android process kill kar de to request pehle khatam ho sakti hai. Screen chhorne/lock karne par hamari recording aur playback stop hoti hain. Reopen ke baad recording khud start nahi hoti.

## Baqi compatibility checks

Basic split-route test pass ho to Snapchat, Instagram, TikTok, Zoom, Google Meet aur imo mein unki available calls, voice notes, video recording aur live features check karein. Har activity ka alag result ho. Screen lock, reconnect aur mid-session switching bhi alag verify karein. Ek app ka pass poore phone ka pass nahi hai.

| Case | Actual mic | Actual output | Result |
|---|---|---|---|
| Own recorder, phone mic | Pending | Playback tested separately | UNVERIFIED |
| Own recorder, earbuds mic | Pending | Playback tested separately | UNVERIFIED |
| WhatsApp voice note | Pending | Pending | UNVERIFIED |
| WhatsApp call | Pending | Pending | UNVERIFIED |
| SIM call | Pending | Pending | UNVERIFIED |
| Mid-session switch | Pending | Pending | UNVERIFIED |
| Screen lock / reconnect | Pending | Pending | UNVERIFIED |
| Other requested apps / activities | Pending | Pending | UNVERIFIED |

## ADB/Shizuku investigation (normal mode fail ho to)

ADB diagnostics apne **local computer** par run karein jahan phone USB debugging ke saath connected aur authorized ho. Remote Codespace phone ko automatically access nahi karta. Phone par routing/settings mutate kiye baghair snapshot:

```bash
python3 tools/collect_device_diagnostics.py --label baseline
# Phone par test karein, phir:
python3 tools/collect_device_diagnostics.py --label whatsapp-call
```

Multiple devices hon to `--serial DEVICE_SERIAL` dein. Script root, `pm grant`, hidden routing calls ya Shizuku installation perform nahi karti. Output `artifacts/device-diagnostics/` mein hoga. Dumps mein app/device identifiers aa sakte hain; share karne se pehle inspect karein.

`MODIFY_AUDIO_ROUTING` ke granted state aur OEM audio-policy dumps ko inspect karke agla backend decide hoga. Permission mil bhi jaye to split routing guaranteed nahi. Is APK mein Shizuku backend nahi hai; actual device evidence ke baghair usay working fallback kehna theek nahi hoga.

## Technical references

- [AudioRecord: preferred device vs actual routed device](https://developer.android.com/reference/android/media/AudioRecord)
- [AudioManager: communication device and requesting-app priority](https://developer.android.com/reference/android/media/AudioManager#setCommunicationDevice(android.media.AudioDeviceInfo))
- [Android microphone capture sharing rules](https://developer.android.com/media/platform/sharing-audio-input)
- [Android 13 permission definitions](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/android13-release/core/res/AndroidManifest.xml)
- [Shizuku privileges](https://shizuku.rikka.app/introduction/)
