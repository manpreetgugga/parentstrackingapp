package com.parentstracker.app.util

import com.parentstracker.app.data.SimpleLanguage

/**
 * The few words simple mode needs, in English, Hindi and Punjabi. The same text is shown in large
 * letters and spoken aloud, so parents who don't read comfortably can still use the phone.
 */
class SimpleText(private val language: SimpleLanguage) {

    private fun pick(en: String, hi: String, pa: String) = when (language) {
        SimpleLanguage.ENGLISH -> en
        SimpleLanguage.HINDI -> hi
        SimpleLanguage.PUNJABI -> pa
    }

    fun greeting(hour: Int) = when (hour) {
        in 4..11 -> pick("Good morning", "सुप्रभात", "ਸ਼ੁਭ ਸਵੇਰ")
        in 12..16 -> pick("Hello", "नमस्ते", "ਸਤ ਸ੍ਰੀ ਅਕਾਲ")
        else -> pick("Good evening", "शुभ संध्या", "ਸ਼ੁਭ ਸ਼ਾਮ")
    }

    val ok get() = pick("I'm OK", "मैं ठीक हूँ", "ਮੈਂ ਠੀਕ ਹਾਂ")
    val okDone get() = pick("Your family knows you are OK", "परिवार को बता दिया कि आप ठीक हैं", "ਪਰਿਵਾਰ ਨੂੰ ਦੱਸ ਦਿੱਤਾ ਕਿ ਤੁਸੀਂ ਠੀਕ ਹੋ")

    fun call(name: String) = pick("Call $name", "$name को फ़ोन करें", "$name ਨੂੰ ਫ਼ੋਨ ਕਰੋ")
    fun calling(name: String) = pick("Calling $name", "$name को फ़ोन लगा रहे हैं", "$name ਨੂੰ ਫ਼ੋਨ ਲਗਾ ਰਹੇ ਹਾਂ")
    val callFamily get() = pick("Call family", "परिवार को फ़ोन करें", "ਪਰਿਵਾਰ ਨੂੰ ਫ਼ੋਨ ਕਰੋ")

    val medicine get() = pick("Medicine", "दवाई", "ਦਵਾਈ")
    val noMedicine get() = pick("No medicine reminders", "कोई दवाई याद नहीं दिलानी", "ਕੋਈ ਦਵਾਈ ਯਾਦ ਨਹੀਂ ਕਰਾਉਣੀ")
    fun nextMedicine(label: String, time: String) =
        pick("Next: $label at $time", "अगली दवाई: $label, $time", "ਅਗਲੀ ਦਵਾਈ: $label, $time")
    val tookIt get() = pick("I took it", "दवाई ले ली", "ਦਵਾਈ ਲੈ ਲਈ")
    fun tookDone(label: String) = pick(
        "Done. Your family knows you took $label",
        "ठीक है। परिवार को बता दिया कि आपने $label ले ली",
        "ਠੀਕ ਹੈ। ਪਰਿਵਾਰ ਨੂੰ ਦੱਸ ਦਿੱਤਾ ਕਿ ਤੁਸੀਂ $label ਲੈ ਲਈ",
    )
    fun medicineTime(label: String) = pick("Time for your medicine: $label", "दवाई का समय: $label", "ਦਵਾਈ ਦਾ ਸਮਾਂ: $label")
    val checkInTime get() = pick("Please tell your family you are OK", "कृपया परिवार को बताएँ कि आप ठीक हैं", "ਕਿਰਪਾ ਕਰਕੇ ਪਰਿਵਾਰ ਨੂੰ ਦੱਸੋ ਕਿ ਤੁਸੀਂ ਠੀਕ ਹੋ")

    val help get() = pick("Help", "मदद", "ਮਦਦ")
    val holdForHelp get() = pick("Hold for 3 seconds", "3 सेकंड दबाकर रखें", "3 ਸਕਿੰਟ ਦਬਾ ਕੇ ਰੱਖੋ")
    val keepHolding get() = pick("Keep holding…", "दबाए रखें…", "ਦਬਾਈ ਰੱਖੋ…")
    val helpSent get() = pick("Help is coming. Your family has been told.", "मदद आ रही है। परिवार को बता दिया गया है।", "ਮਦਦ ਆ ਰਹੀ ਹੈ। ਪਰਿਵਾਰ ਨੂੰ ਦੱਸ ਦਿੱਤਾ ਗਿਆ ਹੈ।")
    val imSafe get() = pick("I'm safe now", "अब मैं सुरक्षित हूँ", "ਹੁਣ ਮੈਂ ਸੁਰੱਖਿਅਤ ਹਾਂ")

    val holdToExit get() = pick("Hold to exit", "बाहर जाने के लिए दबाकर रखें", "ਬਾਹਰ ਜਾਣ ਲਈ ਦਬਾ ਕੇ ਰੱਖੋ")
    val lastCheckIn get() = pick("Last told family", "परिवार को आखिरी बार बताया", "ਪਰਿਵਾਰ ਨੂੰ ਆਖਰੀ ਵਾਰ ਦੱਸਿਆ")
}
