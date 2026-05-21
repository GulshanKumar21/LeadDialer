package com.adyapan.leaddialer

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

object WhatsAppMessageGenerator {
    private const val TAG = "WhatsAppMsgGen"

    val LANGUAGES = listOf(
        "Hindi"      to "हिंदी",
        "English"    to "English",
        "Bengali"    to "বাংলা",
        "Telugu"     to "తెలుగు",
        "Marathi"    to "मराठी",
        "Tamil"      to "தமிழ்",
        "Gujarati"   to "ગુજરાતી",
        "Kannada"    to "ಕನ್ನಡ",
        "Malayalam"  to "മലയാളം",
        "Punjabi"    to "ਪੰਜਾਬੀ",
        "Odia"       to "ଓଡ଼ିଆ",
        "Assamese"   to "অসমীয়া",
        "Urdu"       to "اردو",
        "Sanskrit"   to "संस्कृतम्"
    )

    suspend fun generateMessage(
        context     : Context,
        studentName : String,
        language    : String,
        callStatus  : String = "Interested",
        context_info: String = ""
    ): String {
        return withContext(Dispatchers.IO) {
            // Busy / Not Connected → no AI call, return promo message instantly
            if (callStatus == "Busy" || callStatus == "Not Connected") {
                return@withContext getPromoMessage(studentName, language)
            }

            // Manual dial / no name → AI se personalize nahi ho sakta, promo bhejo
            val isManual = studentName.equals("Manual Dial", ignoreCase = true)
                || studentName.isBlank()
                || studentName.matches(Regex("^[0-9+\\-\\s()]+$"))
            if (isManual) {
                return@withContext getPromoMessage(studentName, language)
            }

            // ── API Key validation ───────────────────────────────────────────
            val apiKey = "YOUR_ANTHROPIC_API_KEY_HERE" // ⚠️ Replace with your real key
            if (apiKey == "YOUR_ANTHROPIC_API_KEY_HERE" || apiKey.isBlank()) {
                Log.w(TAG, "⚠️ API key not set! Returning fallback message.")
                return@withContext getFallbackMessage(studentName, language)
            }

            try {
                val prompt = buildPrompt(studentName, language, callStatus, context_info)

                val requestBody = JSONObject().apply {
                    put("model", "claude-sonnet-4-20250514")
                    put("max_tokens", 500)
                    put("messages", JSONArray().apply {
                        put(JSONObject().apply {
                            put("role", "user")
                            put("content", prompt)
                        })
                    })
                }

                val url  = URL("https://api.anthropic.com/v1/messages")
                val conn = url.openConnection() as HttpURLConnection
                conn.apply {
                    requestMethod = "POST"
                    connectTimeout = 15000
                    readTimeout    = 15000
                    doOutput       = true
                    setRequestProperty("Content-Type", "application/json")
                    setRequestProperty("anthropic-version", "2023-06-01")
                    setRequestProperty("x-api-key", apiKey)
                    outputStream.write(requestBody.toString().toByteArray(Charsets.UTF_8))
                }

                val responseCode = conn.responseCode
                val responseText = if (responseCode == 200) {
                    conn.inputStream.bufferedReader().readText()
                } else {
                    conn.errorStream?.bufferedReader()?.readText() ?: ""
                }
                conn.disconnect()

                Log.d(TAG, "Response code: ${responseCode}")

                if (responseCode == 200) {
                    val json    = JSONObject(responseText)
                    val content = json.getJSONArray("content")
                    val text    = content.getJSONObject(0).getString("text")
                    text.trim()
                } else {
                    Log.e(TAG, "API Error ($responseCode): ${responseText}")
                    getFallbackMessage(studentName, language)
                }

            } catch (e: Exception) {
                Log.e(TAG, "Network error: ${e.message}")
                getFallbackMessage(studentName, language)
            }
        }
    }

    private fun buildPrompt(name: String, language: String, status: String, extraInfo: String): String {
        val isManual = name.equals("Manual Dial", ignoreCase = true) || name.isBlank() || name.matches(Regex("^[0-9+\\\\-\\\\s()]+$"))
        val nameContext = if (isManual) "for a student." else "for a student named \"$name\"."

        val greetingRule = if (isManual) {
            "- Start with a polite greeting in $language (do NOT use any name)"
        } else {
            "- Start with a greeting appropriate for $language using the student's name"
        }

        val statusContext = when (status) {
            "Busy" -> "- Mention that we tried calling them today but they were busy, so we are dropping a message."
            "Not Connected" -> "- Mention that we tried calling them today but could not connect, so we are reaching out here."
            else -> "- Mention that they spoke with Adyapan team and showed interest"
        }

        return """
You are helping an educational company called "Adyapan" send WhatsApp messages to students across India.

CRITICAL INSTRUCTION: Write a warm, friendly WhatsApp message EXCLUSIVELY in $language language $nameContext.
Do NOT write anything in English (unless the selected language is English).

The message should:
$greetingRule
$statusContext
- Tell them Adyapan helps students with their education/career
- Invite them to ask any questions
- End with a warm closing
- Be conversational and friendly, NOT formal
- Use emojis naturally (2-3 emojis max)
- Be 4-6 lines long
- ABSOLUTELY ONLY USE $language SCRIPT/LANGUAGE FOR THE ENTIRE MESSAGE CONTENT.
${if (extraInfo.isNotEmpty()) "- Additional context: $extraInfo" else ""}

Write ONLY the message, nothing else. No explanation, no quotes around it.
        """.trimIndent()
    }

    private fun getFallbackMessage(name: String, language: String): String {
        val isManual = name.equals("Manual Dial", ignoreCase = true) || name.isBlank() || name.matches(Regex("^[0-9+\\\\-\\\\s()]+$"))
        
        return when (language) {
            "Hindi"     -> if (isManual) "नमस्ते! 🙏\n\nAdyapan Team से बात करने के लिए धन्यवाद। हम आपकी शिक्षा में मदद करने के लिए यहाँ हैं। कोई भी सवाल हो तो ज़रूर पूछें! 😊\n\n— Adyapan Team" else "नमस्ते $name! 🙏\n\nAdyapan Team से बात करने के लिए धन्यवाद। हम आपकी शिक्षा में मदद करने के लिए यहाँ हैं। कोई भी सवाल हो तो ज़रूर पूछें! 😊\n\n— Adyapan Team"
            "Tamil"     -> if (isManual) "வணக்கம்! 🙏\n\nAdyapan Team உடன் பேசியதற்கு நன்றி. உங்கள் கல்விக்கு நாங்கள் உதவ தயாராக இருக்கிறோம். ஏதாவது கேள்வி இருந்தால் கேளுங்கள்! 😊\n\n— Adyapan Team" else "வணக்கம் $name! 🙏\n\nAdyapan Team உடன் பேசியதற்கு நன்றி. உங்கள் கல்விக்கு நாங்கள் உதவ தயாராக இருக்கிறோம். ஏதாவது கேள்வி இருந்தால் கேளுங்கள்! 😊\n\n— Adyapan Team"
            "Telugu"    -> if (isManual) "నమస్కారం! 🙏\n\nAdyapan Team తో మాట్లాడినందుకు ధన్యవాదాలు. మీ చదువులో మేము సహాయం చేయడానికి సిద్ధంగా ఉన్నాము. ఏదైనా అడగండి! 😊\n\n— Adyapan Team" else "నమస్కారం $name! 🙏\n\nAdyapan Team తో మాట్లాడినందుకు ధన్యవాదాలు. మీ చదువులో మేము సహాయం చేయడానికి సిద్ధంగా ఉన్నాము. ఏదైనా అడగండి! 😊\n\n— Adyapan Team"
            "Bengali"   -> if (isManual) "নমস্কার! 🙏\n\nAdyapan Team-এর সাথে কথা বলার জন্য ধন্যবাদ। আপনার পড়াশোনায় আমরা সাহায্য করতে প্রস্তুত। যেকোনো প্রশ্ন করুন! 😊\n\n— Adyapan Team" else "নমস্কার $name! 🙏\n\nAdyapan Team-এর সাথে কথা বলার জন্য ধন্যবাদ। আপনার পড়াশোনায় আমরা সাহায্য করতে প্রস্তুত। যেকোনো প্রশ্ন করুন! 😊\n\n— Adyapan Team"
            "Marathi"   -> if (isManual) "नमस्कार! 🙏\n\nAdyapan Team शी बोलल्याबद्दल धन्यवाद. तुमच्या शिक्षणात मदत करण्यासाठी आम्ही तयार आहोत. कोणताही प्रश्न विचारा! 😊\n\n— Adyapan Team" else "नमस्कार $name! 🙏\n\nAdyapan Team शी बोलल्याबद्दल धन्यवाद. तुमच्या शिक्षणात मदत करण्यासाठी आम्ही तयार आहोत. कोणताही प्रश्न विचारा! 😊\n\n— Adyapan Team"
            "Gujarati"  -> if (isManual) "નમસ્તે! 🙏\n\nAdyapan Team સાથે વાત કરવા બદલ આભાર. તમારા ભણતરમાં મદદ કરવા અમે તૈયાર છીએ. કોઈ પ્રશ્ન હોય તો ચોક્કસ પૂછો! 😊\n\n— Adyapan Team" else "નમસ્તે $name! 🙏\n\nAdyapan Team સાથે વાત કરવા બદલ આભાર. તમારા ભણતરમાં મદદ કરવા અમે તૈયાર છીએ. કોઈ પ્રશ્ન હોય તો ચોક્કસ પૂછો! 😊\n\n— Adyapan Team"
            "Kannada"   -> if (isManual) "ನಮಸ್ಕಾರ! 🙏\n\nAdyapan Team ನೊಂದಿಗೆ ಮಾತನಾಡಿದ್ದಕ್ಕೆ ಧನ್ಯವಾದಗಳು. ನಿಮ್ಮ ಶಿಕ್ಷಣದಲ್ಲಿ ಸಹಾಯ ಮಾಡಲು ನಾವು ಸಿದ್ಧರಿದ್ದೇವೆ. ಯಾವುದೇ ಪ್ರಶ್ನೆ ಕೇಳಿ! 😊\n\n— Adyapan Team" else "ನಮಸ್ಕಾರ $name! 🙏\n\nAdyapan Team ನೊಂದಿಗೆ ಮಾತನಾಡಿದ್ದಕ್ಕೆ ಧನ್ಯವಾದಗಳು. ನಿಮ್ಮ ಶಿಕ್ಷಣದಲ್ಲಿ ಸಹಾಯ ಮಾಡಲು ನಾವು ಸಿದ್ಧರಿದ್ದೇವೆ. ಯಾವುದೇ ಪ್ರಶ್ನೆ ಕೇಳಿ! 😊\n\n— Adyapan Team"
            "Malayalam" -> if (isManual) "നമസ്കാരം! 🙏\n\nAdyapan Team-മായി സംസാരിച്ചതിന് നന്ദി. നിങ്ങളുടെ പഠനത്തിൽ സഹായിക്കാൻ ഞങ്ങൾ തയ്യാറാണ്. എന്തും ചോദിക്കൂ! 😊\n\n— Adyapan Team" else "നമസ്കാരം $name! 🙏\n\nAdyapan Team-മായി സംസാരിച്ചതിന് നന്ദി. നിങ്ങളുടെ പഠനത്തിൽ സഹായിക്കാൻ ഞങ്ങൾ തയ്യാറാണ്. എന്തും ചോദിക്കൂ! 😊\n\n— Adyapan Team"
            "Punjabi"   -> if (isManual) "ਸਤ ਸ੍ਰੀ ਅਕਾਲ! 🙏\n\nAdyapan Team ਨਾਲ ਗੱਲ ਕਰਨ ਲਈ ਧੰਨਵਾਦ। ਤੁਹਾਡੀ ਪੜ੍ਹਾਈ ਵਿੱਚ ਮਦਦ ਕਰਨ ਲਈ ਅਸੀਂ ਤਿਆਰ ਹਾਂ। ਕੋਈ ਵੀ ਸਵਾਲ ਪੁੱਛੋ! 😊\n\n— Adyapan Team" else "ਸਤ ਸ੍ਰੀ ਅਕਾਲ $name! 🙏\n\nAdyapan Team ਨਾਲ ਗੱਲ ਕਰਨ ਲਈ ਧੰਨਵਾਦ। ਤੁਹਾਡੀ ਪੜ੍ਹਾਈ ਵਿੱਚ ਮਦਦ ਕਰਨ ਲਈ ਅਸੀਂ ਤਿਆਰ ਹਾਂ। ਕੋਈ ਵੀ ਸਵਾਲ ਪੁੱਛੋ! 😊\n\n— Adyapan Team"
            else        -> if (isManual) "Hello! 🙏\n\nThank you for speaking with the Adyapan Team. We are here to help with your education. Feel free to ask any questions! 😊\n\n— Adyapan Team" else "Hello $name! 🙏\n\nThank you for speaking with the Adyapan Team. We are here to help with your education. Feel free to ask any questions! 😊\n\n— Adyapan Team"
        }
    }

    // ─── Promo message dispatcher (Busy / Not Connected) ───────────────────────
    private fun getPromoMessage(name: String, language: String): String {
        val isManual = name.equals("Manual Dial", ignoreCase = true)
            || name.isBlank()
            || name.matches(Regex("^[0-9+\\-\\s()]+$"))
        val n = if (isManual) "" else name
        return when (language) {
            "Hindi"     -> promoHindi(n)
            "Bengali"   -> promoBengali(n)
            "Telugu"    -> promoTelugu(n)
            "Marathi"   -> promoMarathi(n)
            "Tamil"     -> promoTamil(n)
            "Gujarati"  -> promoGujarati(n)
            "Kannada"   -> promoKannada(n)
            "Malayalam" -> promoMalayalam(n)
            "Punjabi"   -> promoPunjabi(n)
            "Odia"      -> promoOdia(n)
            "Assamese"  -> promoAssamese(n)
            "Urdu"      -> promoUrdu(n)
            else        -> promoEnglish(n)
        }
    }

    // ─── Promo functions — aap yahan apna translated text paste karo ───────────

    private fun promoHindi(name: String): String {
        val g = if (name.isBlank()) "⚠ नमस्ते," else "⚠ $name,"
        return """$g यह वो बात है जो ज़्यादातर छात्र थोड़ी देर से समझते हैं…

"कोर्स पूरा करना" और सच में industry-ready बनना — इन दोनों में बहुत बड़ा फ़र्क है।

इसीलिए छात्र Adyapan से अलग तरह से जुड़ते हैं।

✅ Live Practical Sessions
✅ Real Projects & Implementation
✅ Mock Interviews & Interview Drills
✅ Resume & LinkedIn Building
✅ Internship & Placement Support
✅ चुनिंदा programs में ₹15,000 तक Stipend

🏆 ISO Certified & NSDC Certified Courses
🏆 Cisco, Adobe, Microsoft, Apple, Meta से जुड़े Certifications

यहाँ mentors और industry professionals आपको practical growth, confidence और real implementation की तरफ guide करते हैं — सिर्फ theory नहीं।

⏳ 1 Month | 45 Days | 4 Months
💰 सिर्फ ₹2999 से शुरू

और सच कहें तो, कई छात्र अपने दोस्तों & seniors की recommendation पर यहाँ आते हैं — यहाँ मिलने वाले exposure & support की वजह से 🚀""".trimIndent()
    }

    private fun promoEnglish(name: String): String {
        val g = if (name.isBlank()) "⚠ Hello," else "⚠ $name,"
        return """$g this is the kind of thing students usually realise a little too late…

There's a huge difference between
"finishing a course"
and actually becoming industry-ready.

That's why students connect with Adyapan differently.

✅ Live practical sessions
✅ Real projects & implementation
✅ Mock interviews & interview drills
✅ Resume & LinkedIn building
✅ Internship & placement support
✅ Stipend opportunities up to ₹15,000 in selected programs

🏆 ISO Certified & NSDC Certified courses
🏆 Certifications associated with Cisco, Adobe, Microsoft, Apple, Meta & more

Students here are guided by mentors & industry professionals focused on practical growth, confidence & real implementation — not just theory.

⏳ 1 Month | 45 Days | 4 Months
💰 Starting from just ₹2999

And honestly, many students join through recommendations from friends & seniors because of the exposure & support they experience here 🚀""".trimIndent()
    }

    // TODO: Aap yahan baaki languages ka text paste karo
    private fun promoBengali(name: String): String {
        val g = if (name.isBlank()) "⚠ হ্যালো," else "⚠ $name,"
        return """$g এটা সেই বিষয় যা বেশিরভাগ শিক্ষার্থীরা একটু দেরিতে বুঝতে পারে…

"কোর্স শেষ করা" আর সত্যিকার অর্থে industry-ready হওয়া — এই দুটোর মধ্যে অনেক বড় পার্থক্য আছে।

তাই শিক্ষার্থীরা Adyapan-এর সাথে আলাদাভাবে সংযুক্ত হয়।

✅ Live Practical Sessions
✅ Real Projects & Implementation
✅ Mock Interviews & Interview Drills
✅ Resume & LinkedIn Building
✅ Internship & Placement Support
✅ নির্বাচিত programs-এ ₹15,000 পর্যন্ত Stipend

🏆 ISO Certified & NSDC Certified Courses
🏆 Cisco, Adobe, Microsoft, Apple, Meta-র সাথে সম্পর্কিত Certifications

এখানে mentors এবং industry professionals শিক্ষার্থীদের practical growth, confidence এবং real implementation-এর দিকে গাইড করেন — শুধু theory নয়।

⏳ 1 Month | 45 Days | 4 Months
💰 মাত্র ₹2999 থেকে শুরু

এবং সত্যি বলতে, অনেক শিক্ষার্থী বন্ধু ও সিনিয়রদের recommendation-এ এখানে যোগ দেয় — এখানকার exposure ও support-এর কারণে 🚀""".trimIndent()
    }
    private fun promoTelugu(name: String): String {
        val g = if (name.isBlank()) "⚠ నమస్కారం," else "⚠ $name,"
        return """$g చాలా మంది విద్యార్థులు ఈ విషయం కొంచెం ఆలస్యంగా గ్రహిస్తారు…

"కోర్సు పూర్తి చేయడం" మరియు నిజంగా industry-ready అవడం — ఈ రెండింటిలో చాలా పెద్ద తేడా ఉంది.

అందుకే విద్యార్థులు Adyapan తో వేరేగా అనుసంధానమవుతారు.

✅ Live Practical Sessions
✅ Real Projects & Implementation
✅ Mock Interviews & Interview Drills
✅ Resume & LinkedIn Building
✅ Internship & Placement Support
✅ ఎంచుకున్న programs లో ₹15,000 వరకు Stipend

🏆 ISO Certified & NSDC Certified Courses
🏆 Cisco, Adobe, Microsoft, Apple, Meta తో అనుబంధ Certifications

ఇక్కడ mentors మరియు industry professionals విద్యార్థులను practical growth, confidence మరియు real implementation వైపు నడిపిస్తారు — కేవలం theory కాదు.

⏳ 1 Month | 45 Days | 4 Months
💰 కేవలం ₹2999 నుండి మొదలు

నిజంగా చెప్పాలంటే, చాలా మంది విద్యార్థులు తమ స్నేహితులు & సీనియర్ల recommendation తో ఇక్కడ చేరతారు — ఇక్కడి exposure & support వల్ల 🚀""".trimIndent()
    }
    private fun promoMarathi(name: String): String {
        val g = if (name.isBlank()) "⚠ नमस्कार," else "⚠ $name,"
        return """$g ही अशी गोष्ट आहे जी बहुतेक विद्यार्थ्यांना थोड्या उशिराने कळते…

"कोर्स पूर्ण करणे" आणि खरोखर industry-ready होणे — या दोन्हीत खूप मोठा फरक आहे.

म्हणूनच विद्यार्थी Adyapan शी वेगळ्या प्रकारे जोडतात.

✅ Live Practical Sessions
✅ Real Projects & Implementation
✅ Mock Interviews & Interview Drills
✅ Resume & LinkedIn Building
✅ Internship & Placement Support
✅ निवडक programs मध्ये ₹15,000 पर्यंत Stipend

🏆 ISO Certified & NSDC Certified Courses
🏆 Cisco, Adobe, Microsoft, Apple, Meta शी संबंधित Certifications

येथे mentors आणि industry professionals विद्यार्थ्यांना practical growth, confidence आणि real implementation कडे मार्गदर्शन करतात — फक्त theory नाही.

⏳ 1 Month | 45 Days | 4 Months
💰 फक्त ₹2999 पासून सुरुवात

आणि खरं सांगायचं तर, अनेक विद्यार्थी मित्र व सीनियर्सच्या शिफारसीमुळे इथे येतात — येथील exposure आणि support मुळे 🚀""".trimIndent()
    }
    private fun promoTamil(name: String): String {
        val g = if (name.isBlank()) "⚠ வணக்கம்," else "⚠ $name,"
        return """$g இது பெரும்பாலான மாணவர்கள் சற்று தாமதமாக உணர்வது…

"கோர்ஸ் முடிப்பது" மற்றும் உண்மையிலேயே industry-ready ஆவது — இந்த இரண்டுக்கும் மிகவும் பெரிய வித்தியாசம் உள்ளது.

அதனால்தான் மாணவர்கள் Adyapan உடன் வித்தியாசமாக இணைகிறார்கள்.

✅ Live Practical Sessions
✅ Real Projects & Implementation
✅ Mock Interviews & Interview Drills
✅ Resume & LinkedIn Building
✅ Internship & Placement Support
✅ தேர்ந்தெடுக்கப்பட்ட programs இல் ₹15,000 வரை Stipend

🏆 ISO Certified & NSDC Certified Courses
🏆 Cisco, Adobe, Microsoft, Apple, Meta உடன் தொடர்புடைய Certifications

இங்கு mentors மற்றும் industry professionals மாணவர்களை practical growth, confidence மற்றும் real implementation நோக்கி வழிகாட்டுகிறார்கள் — வெறும் theory மட்டுமல்ல.

⏳ 1 Month | 45 Days | 4 Months
💰 வெறும் ₹2999 இலிருந்து தொடங்குகிறது

உண்மையில், பல மாணவர்கள் தங்கள் நண்பர்கள் & சீனியர்களின் பரிந்துரையால் இங்கு சேர்கிறார்கள் — இங்குள்ள exposure & support காரணமாக 🚀""".trimIndent()
    }
    private fun promoGujarati(name: String): String {
        val g = if (name.isBlank()) "⚠ નમસ્તે," else "⚠ $name,"
        return """$g આ એ વાત છે જે મોટાભાગના વિદ્યાર્થીઓ થોડી મોડી સમજે છે…

"કોર્સ પૂરો કરવો" અને ખરેખર industry-ready બનવું — આ બે વચ્ચે ઘણો મોટો ફરક છે.

એટલે જ વિદ્યાર્થીઓ Adyapan સાથે અલગ રીતે જોડાય છે.

✅ Live Practical Sessions
✅ Real Projects & Implementation
✅ Mock Interviews & Interview Drills
✅ Resume & LinkedIn Building
✅ Internship & Placement Support
✅ પસંદ કરેલ programs માં ₹15,000 સુધી Stipend

🏆 ISO Certified & NSDC Certified Courses
🏆 Cisco, Adobe, Microsoft, Apple, Meta સાથે સંકળાયેલ Certifications

અહીં mentors અને industry professionals વિદ્યાર્થીઓને practical growth, confidence અને real implementation તરફ માર્ગદર્શન આપે છે — ફક્ત theory નહીં.

⏳ 1 Month | 45 Days | 4 Months
💰 માત્ર ₹2999 થી શરૂ

અને સાચું કહીએ તો, ઘણા વિદ્યાર્થીઓ મિત્રો & સિનિયર્સની ભલામણ પર અહીં જોડાય છે — અહીં મળતા exposure & support ને કારણે 🚀""".trimIndent()
    }
    private fun promoKannada(name: String): String {
        val g = if (name.isBlank()) "⚠ ನಮಸ್ಕಾರ," else "⚠ $name,"
        return """$g ಇದು ಹೆಚ್ಚಿನ ವಿದ್ಯಾರ್ಥಿಗಳು ಸ್ವಲ್ಪ ತಡವಾಗಿ ಅರಿಯುವ ವಿಷಯ…

"ಕೋರ್ಸ್ ಮುಗಿಸುವುದು" ಮತ್ತು ನಿಜವಾಗಿ industry-ready ಆಗುವುದು — ಇವೆರಡರ ನಡುವೆ ದೊಡ್ಡ ವ್ಯತ್ಯಾಸವಿದೆ.

ಅದಕ್ಕಾಗಿಯೇ ವಿದ್ಯಾರ್ಥಿಗಳು Adyapan ನೊಂದಿಗೆ ಭಿನ್ನವಾಗಿ ಸಂಪರ್ಕ ಸಾಧಿಸುತ್ತಾರೆ.

✅ Live Practical Sessions
✅ Real Projects & Implementation
✅ Mock Interviews & Interview Drills
✅ Resume & LinkedIn Building
✅ Internship & Placement Support
✅ ಆಯ್ದ programs ನಲ್ಲಿ ₹15,000 ವರೆಗೆ Stipend

🏆 ISO Certified & NSDC Certified Courses
🏆 Cisco, Adobe, Microsoft, Apple, Meta ಜೊತೆ ಸಂಬಂಧಿತ Certifications

ಇಲ್ಲಿ mentors ಮತ್ತು industry professionals ವಿದ್ಯಾರ್ಥಿಗಳನ್ನು practical growth, confidence ಮತ್ತು real implementation ಕಡೆಗೆ ಮಾರ್ಗದರ್ಶನ ಮಾಡುತ್ತಾರೆ — ಕೇವಲ theory ಅಲ್ಲ.

⏳ 1 Month | 45 Days | 4 Months
💰 ಕೇವಲ ₹2999 ದಿಂದ ಆರಂಭ

ನಿಜವಾಗಿ ಹೇಳಬೇಕೆಂದರೆ, ಅನೇಕ ವಿದ್ಯಾರ್ಥಿಗಳು ಸ್ನೇಹಿತರು & ಸೀನಿಯರ್‌ಗಳ ಶಿಫಾರಸಿನ ಮೇರೆಗೆ ಇಲ್ಲಿ ಸೇರುತ್ತಾರೆ — ಇಲ್ಲಿನ exposure & support ಕಾರಣದಿಂದ 🚀""".trimIndent()
    }
    private fun promoMalayalam(name: String): String {
        val g = if (name.isBlank()) "⚠ നമസ്കാരം," else "⚠ $name,"
        return """$g ഇത് മിക്ക വിദ്യാർത്ഥികളും അൽപം വൈകി മനസ്സിലാക്കുന്ന ഒന്നാണ്…

"കോഴ്‌സ് പൂർത്തിയാക്കൽ" vs ശരിക്കും industry-ready ആകൽ — ഈ രണ്ടിനും ഇടയിൽ വലിയ വ്യത്യാസമുണ്ട്.

അതുകൊണ്ടാണ് വിദ്യാർത്ഥികൾ Adyapan-ൽ വ്യത്യസ്തമായി ബന്ധപ്പെടുന്നത്.

✅ Live Practical Sessions
✅ Real Projects & Implementation
✅ Mock Interviews & Interview Drills
✅ Resume & LinkedIn Building
✅ Internship & Placement Support
✅ തിരഞ്ഞെടുത്ത programs-ൽ ₹15,000 വരെ Stipend

🏆 ISO Certified & NSDC Certified Courses
🏆 Cisco, Adobe, Microsoft, Apple, Meta-യുമായി ബന്ധപ്പെട്ട Certifications

ഇവിടെ mentors-ഉം industry professionals-ഉം വിദ്യാർത്ഥികളെ practical growth, confidence, real implementation-ലേക്ക് നയിക്കുന്നു — കേവലം theory മാത്രമല്ല.

⏳ 1 Month | 45 Days | 4 Months
💰 വെറും ₹2999 മുതൽ

സത്യത്തിൽ, പല വിദ്യാർത്ഥികളും സുഹൃത്തുക്കളുടെയും സീനിയർമാരുടെയും ശുപാർശ പ്രകാരം ഇവിടെ ചേരുന്നു — ഇവിടുത്തെ exposure & support കൊണ്ട് 🚀""".trimIndent()
    }
    private fun promoPunjabi(name: String): String {
        val g = if (name.isBlank()) "⚠ ਸਤ ਸ੍ਰੀ ਅਕਾਲ," else "⚠ $name,"
        return """$g ਇਹ ਉਹ ਗੱਲ ਹੈ ਜੋ ਜ਼ਿਆਦਾਤਰ ਵਿਦਿਆਰਥੀ ਥੋੜੀ ਦੇਰ ਨਾਲ ਸਮਝਦੇ ਹਨ…

"ਕੋਰਸ ਪੂਰਾ ਕਰਨਾ" ਅਤੇ ਸੱਚਮੁੱਚ industry-ready ਬਣਨਾ — ਇਨ੍ਹਾਂ ਦੋਵਾਂ ਵਿੱਚ ਬਹੁਤ ਵੱਡਾ ਫ਼ਰਕ ਹੈ।

ਇਸ ਲਈ ਵਿਦਿਆਰਥੀ Adyapan ਨਾਲ ਵੱਖਰੇ ਤਰੀਕੇ ਨਾਲ ਜੁੜਦੇ ਹਨ।

✅ Live Practical Sessions
✅ Real Projects & Implementation
✅ Mock Interviews & Interview Drills
✅ Resume & LinkedIn Building
✅ Internship & Placement Support
✅ ਚੁਣੇ ਹੋਏ programs ਵਿੱਚ ₹15,000 ਤੱਕ Stipend

🏆 ISO Certified & NSDC Certified Courses
🏆 Cisco, Adobe, Microsoft, Apple, Meta ਨਾਲ ਜੁੜੇ Certifications

ਇੱਥੇ mentors ਅਤੇ industry professionals ਵਿਦਿਆਰਥੀਆਂ ਨੂੰ practical growth, confidence ਅਤੇ real implementation ਵੱਲ ਮਾਰਗਦਰਸ਼ਨ ਦਿੰਦੇ ਹਨ — ਸਿਰਫ਼ theory ਨਹੀਂ।

⏳ 1 Month | 45 Days | 4 Months
💰 ਸਿਰਫ਼ ₹2999 ਤੋਂ ਸ਼ੁਰੂ

ਅਤੇ ਸੱਚ ਕਹਾਂ ਤਾਂ, ਕਈ ਵਿਦਿਆਰਥੀ ਆਪਣੇ ਦੋਸਤਾਂ ਅਤੇ ਸੀਨੀਅਰਜ਼ ਦੀ ਸਿਫ਼ਾਰਸ਼ ਤੇ ਇੱਥੇ ਆਉਂਦੇ ਹਨ — ਇੱਥੋਂ ਦੇ exposure ਅਤੇ support ਕਾਰਨ 🚀""".trimIndent()
    }
    private fun promoOdia(name: String): String {
        val g = if (name.isBlank()) "⚠ ନମସ୍କାର," else "⚠ $name,"
        return """$g ଏହା ସେହି ଜିନିଷ ଯାହା ଅଧିକାଂଶ ଛାତ୍ରଛାତ୍ରୀ ଟିକେ ଦେରିରେ ବୁଝନ୍ତି…

"ଏକ କୋର୍ସ ସରିବା" ଏବଂ ପ୍ରକୃତ ଅର୍ଥରେ industry-ready ହେବା — ଏ ଦୁଇ ମଧ୍ୟରେ ବହୁ ବଡ ଫରକ ଅଛି।

ଏଥିପାଇଁ ଛାତ୍ରଛାତ୍ରୀ Adyapan ସହ ଅଲଗା ଭାବରେ ଯୋଡ଼ି ହୋଇଥାନ୍ତି।

✅ Live Practical Sessions
✅ Real Projects & Implementation
✅ Mock Interviews & Interview Drills
✅ Resume & LinkedIn Building
✅ Internship & Placement Support
✅ ଚୟନିତ programs ରେ ₹15,000 ପର୍ଯ୍ୟନ୍ତ Stipend

🏆 ISO Certified & NSDC Certified Courses
🏆 Cisco, Adobe, Microsoft, Apple, Meta ସହ ଜଡ଼ିତ Certifications

ଏଠାରେ mentors ଏବଂ industry professionals ଛାତ୍ରଛାତ୍ରୀଙ୍କୁ practical growth, confidence ଏବଂ real implementation ଆଡ଼କୁ ମାର୍ଗଦର୍ଶନ ଦିଅନ୍ତି — ଶୁଧୁ theory ନୁହେଁ।

⏳ 1 Month | 45 Days | 4 Months
💰 ମାତ୍ର ₹2999 ରୁ ଆରମ୍ଭ

ଆଉ ସତ କହିବାକୁ ଗଲେ, ବହୁ ଛାତ୍ରଛାତ୍ରୀ ବନ୍ଧୁ ଓ ସିନିୟରଙ୍କ ପରାମର୍ଶରେ ଏଠାରେ ଯୋଗ ଦିଅନ୍ତି — ଏଠାର exposure ଓ support ଯୋଗୁ 🚀""".trimIndent()
    }
    private fun promoAssamese(name: String): String {
        val g = if (name.isBlank()) "⚠ নমস্কার," else "⚠ $name,"
        return """$g এইটো সেই কথা যা বেশিভাগ শিক্ষার্থীয়ে কিছু দেরিকৈ বুজি পায়…

"কোর্চ শেষ করা" আরু সঁচাকৈ industry-ready হোৱা — এই দুটার মাজত বহুত পার্থক্য আছে।

সেইকারণেই শিক্ষার্থীসকল Adyapan-র সৈতে বেলেগ ধরণে সংযুক্ত হয়।

✅ Live Practical Sessions
✅ Real Projects & Implementation
✅ Mock Interviews & Interview Drills
✅ Resume & LinkedIn Building
✅ Internship & Placement Support
✅ নির্বাচিত programs-ত ১৫,000 লৈকে Stipend

🏆 ISO Certified & NSDC Certified Courses
🏆 Cisco, Adobe, Microsoft, Apple, Meta-র সৈতে সম্পর্কিত Certifications

ইয়াত mentors আরু industry professionals শিক্ষার্থীসকলক practical growth, confidence আরু real implementation-র দিশে পথ দেখুৱায় — কেৱল theory নহয়।

⏳ 1 Month | 45 Days | 4 Months
💰 মাত্র ₹2999 র পরা আরম্ভ

আরু সঁচাকৈ কবালৈ গলে, বহু শিক্ষার্থী বন্ধু আরু চিনিয়রর পরামর্শত ইয়ালৈ আহে — ইয়াত পোৱা exposure আরু support-র বাবে 🚀""".trimIndent()
    }
    private fun promoUrdu(name: String): String {
        val g = if (name.isBlank()) "⚠ اسلام علیکم," else "⚠ $name,"
        return """$g یہ وہ بات ہے جو اکثر طلباء کو تھوڑی دیر سے سمجھ آتی ہے…

"کورس مکمل کرنا" اور واقعی industry-ready بننا — ان دونوں میں بہت بڑا فرق ہے۔

اسی لیے طلباء Adyapan سے مختلف طریقے سے جڑتے ہیں۔

✅ Live Practical Sessions
✅ Real Projects & Implementation
✅ Mock Interviews & Interview Drills
✅ Resume & LinkedIn Building
✅ Internship & Placement Support
✅ منتخب programs میں ₹15,000 تک Stipend

🏆 ISO Certified & NSDC Certified Courses
🏆 Cisco، Adobe، Microsoft، Apple، Meta سے منسلک Certifications

یہاں mentors اور industry professionals طلباء کو practical growth، confidence اور real implementation کی طرف رہنمائی کرتے ہیں — صرف theory نہیں۔

⏳ 1 Month | 45 Days | 4 Months
💰 صرف ₹2999 سے شروع

اور سچ کہوں تو، بہت سے طلباء اپنے دوستوں اور سینیئرز کی سفارش پر یہاں آتے ہیں — یہاں کے exposure اور support کی وجہ سے 🚀""".trimIndent()
    }
}