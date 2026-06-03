package com.example.scanner

import android.graphics.*
import android.graphics.pdf.PdfDocument
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

// Represents a page in the active PDF creation queue
data class ScanPage(
    val id: String,
    val originalImagePath: String, // local file path to raw photo
    var rotationDegrees: Float = 0f,
    // perspective warp corners relative to source coordinates 0f to 1f
    var topLeftX: Float = 0.05f, var topLeftY: Float = 0.05f,
    var topRightX: Float = 0.95f, var topRightY: Float = 0.05f,
    var bottomRightX: Float = 0.95f, var bottomRightY: Float = 0.95f,
    var bottomLeftX: Float = 0.05f, var bottomLeftY: Float = 0.95f,
    var brightness: Float = 1.0f,
    var contrast: Float = 1.0f,
    var thresholdingEnabled: Boolean = false // clean b&w balance optimization
)

object PdfGenerator {
    
    // Warps an image based on crop coordinates and returns flat scan bitmap
    suspend fun processScanPage(
        contextUriPath: String,
        page: ScanPage
    ): Bitmap? = withContext(Dispatchers.IO) {
        val file = File(page.originalImagePath)
        if (!file.exists()) return@withContext null

        try {
            // Load original photo
            val srcBmp = BitmapFactory.decodeFile(file.absolutePath) ?: return@withContext null
            val w = srcBmp.width.toFloat()
            val h = srcBmp.height.toFloat()

            // 1. Rotation first
            val rotatedBmp = if (page.rotationDegrees != 0f) {
                val matrix = Matrix().apply { postRotate(page.rotationDegrees) }
                val rotated = Bitmap.createBitmap(srcBmp, 0, 0, srcBmp.width, srcBmp.height, matrix, true)
                if (rotated != srcBmp) srcBmp.recycle()
                rotated
            } else {
                srcBmp
            }

            val rw = rotatedBmp.width.toFloat()
            val rh = rotatedBmp.height.toFloat()

            // 2. Perspective Warp (setPolyToPoly)
            val destW = 1200
            val destH = 1600
            val warpedBmp = Bitmap.createBitmap(destW, destH, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(warpedBmp)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

            // Source polygon corners
            val srcPoints = floatArrayOf(
                page.topLeftX * rw, page.topLeftY * rh,
                page.topRightX * rw, page.topRightY * rh,
                page.bottomRightX * rw, page.bottomRightY * rh,
                page.bottomLeftX * rw, page.bottomLeftY * rh
            )

            // Destination flat rectangular corners
            val dstPoints = floatArrayOf(
                0f, 0f,
                destW.toFloat(), 0f,
                destW.toFloat(), destH.toFloat(),
                0f, destH.toFloat()
            )

            val matrix = Matrix()
            val success = matrix.setPolyToPoly(srcPoints, 0, dstPoints, 0, 4)
            if (success) {
                canvas.drawBitmap(rotatedBmp, matrix, paint)
                rotatedBmp.recycle()
            } else {
                // fall back to simple stretch if matrix layout fails
                val rectDst = Rect(0, 0, destW, destH)
                canvas.drawBitmap(rotatedBmp, null, rectDst, paint)
                rotatedBmp.recycle()
            }

            // 3. Brightness, Contrast & B&W document enhancement
            val finalBmp = Bitmap.createBitmap(destW, destH, Bitmap.Config.ARGB_8888)
            val pCanvas = Canvas(finalBmp)
            val colorMatrix = ColorMatrix()

            // Contrast scale & brightness translate
            val scale = page.contrast
            val translate = (page.brightness - 1f) * 255f
            val cmArr = floatArrayOf(
                scale, 0f, 0f, 0f, translate,
                0f, scale, 0f, 0f, translate,
                0f, 0f, scale, 0f, translate,
                0f, 0f, 0f, 1f, 0f
            )
            colorMatrix.set(cmArr)

            // High Contrast B&W Document mode to strip desk shadow
            if (page.thresholdingEnabled) {
                // Desaturate completely first
                val grayscale = ColorMatrix()
                grayscale.setSaturation(0f)
                colorMatrix.postConcat(grayscale)

                // High-pass thresholding simulation via high contrast multiplication
                val threshMat = ColorMatrix(floatArrayOf(
                    4f,  0f,  0f, 0f, -380f,
                    0f,  4f,  0f, 0f, -380f,
                    0f,  0f,  4f, 0f, -380f,
                    0f,  0f,  0f, 1f, 0f
                ))
                colorMatrix.postConcat(threshMat)
            }

            val filterPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                colorFilter = ColorMatrixColorFilter(colorMatrix)
            }

            pCanvas.drawBitmap(warpedBmp, 0f, 0f, filterPaint)
            warpedBmp.recycle()

            finalBmp
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    // Compiles a list of processed scan pages into a single high-quality PDF
    suspend fun createPdf(
        outputFile: File,
        pages: List<ScanPage>
    ): Boolean = withContext(Dispatchers.IO) {
        if (pages.isEmpty()) return@withContext false

        val document = PdfDocument()
        try {
            for ((index, scanPage) in pages.withIndex()) {
                val pageBmp = processScanPage(scanPage.originalImagePath, scanPage) ?: continue

                // Standard letter/A4 proportional dimensions in PDF points (72 points/inch)
                // A4 is 595 x 842 points.
                val widthPt = 595
                val heightPt = 842

                val pageInfo = PdfDocument.PageInfo.Builder(widthPt, heightPt, index).create()
                val pdfPage = document.startPage(pageInfo)
                val canvas = pdfPage.canvas

                // Draw bitmap scaling to fill the point constraints
                val srcRect = Rect(0, 0, pageBmp.width, pageBmp.height)
                val dstRect = RectF(0f, 0f, widthPt.toFloat(), heightPt.toFloat())
                canvas.drawBitmap(pageBmp, srcRect, dstRect, Paint(Paint.FILTER_BITMAP_FLAG))

                document.finishPage(pdfPage)
                pageBmp.recycle()
            }

            if (document.pages.isEmpty()) {
                return@withContext false
            }

            val fos = FileOutputStream(outputFile)
            document.writeTo(fos)
            fos.close()
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        } finally {
            document.close()
        }
    }

    suspend fun generateEBookPdf(
        outputFile: File,
        title: String,
        author: String,
        coverHexColor: String,
        coverStyle: String,
        coverPattern: String,
        contentPreset: String
    ): Boolean = withContext(Dispatchers.IO) {
        val document = PdfDocument()
        try {
            // A4 size: 595 x 842 points
            val widthPt = 595
            val heightPt = 842

            // ==================== PAGE 1: COVER PAGE ====================
            val infoCover = PdfDocument.PageInfo.Builder(widthPt, heightPt, 0).create()
            val pageCover = document.startPage(infoCover)
            val canvas = pageCover.canvas

            // Fill cover background
            val paint = Paint(Paint.ANTI_ALIAS_FLAG)
            val coverColorInt = try {
                android.graphics.Color.parseColor(coverHexColor)
            } catch (e: Exception) {
                0xFF1E88E5.toInt() // fallback blue
            }
            
            // Background
            paint.color = coverColorInt
            canvas.drawRect(0f, 0f, widthPt.toFloat(), heightPt.toFloat(), paint)

            // Draw some decorative patterns based on pattern and style
            paint.color = 0x22FFFFFF // translucent white for patterns
            if (coverPattern == "Modern Gradient" || coverPattern == "Stars & Galaxy") {
                // Galaxy/stars
                val r = java.util.Random(42)
                for (i in 0 until 120) {
                    val rx = r.nextFloat() * widthPt
                    val ry = r.nextFloat() * heightPt
                    val rRadius = r.nextFloat() * 3f + 1f
                    canvas.drawCircle(rx, ry, rRadius, paint)
                }
            } else if (coverPattern == "Classic Vintage" || coverPattern == "Golden Filigree") {
                // Double Border frame
                paint.color = 0xFFD4AF37.toInt() // Gold color
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = 3f
                canvas.drawRect(20f, 20f, widthPt - 20f, heightPt - 20f, paint)
                paint.strokeWidth = 1f
                canvas.drawRect(26f, 26f, widthPt - 26f, heightPt - 26f, paint)
                paint.style = Paint.Style.FILL
            } else if (coverPattern == "Cyber Punk" || coverPattern == "Cyberpunk Glow") {
                // Sharp diagonal lines
                paint.color = 0x3300FFFF // Translucent cyan
                paint.strokeWidth = 2f
                for (i in 0 until 10) {
                    val offset = i * 60f
                    canvas.drawLine(offset, 0f, offset + 200f, heightPt.toFloat(), paint)
                }
            } else {
                // General Stripe / Wave pattern
                for (i in 0 until 5) {
                    canvas.drawRect(0f, 150f + (i * 100), widthPt.toFloat(), 180f + (i * 100), paint)
                }
            }

            // Draw Central Label Plaque
            if (coverStyle == "Vintage Classic" || coverStyle == "Golden Filigree") {
                // Classic cream rectangle box inside
                paint.color = 0xFFFCFBE3.toInt() // Old book paper cream
                paint.style = Paint.Style.FILL
                val plaqueLeft = 80f
                val plaqueTop = 220f
                val plaqueRight = widthPt - 80f
                val plaqueBottom = 540f
                canvas.drawRect(plaqueLeft, plaqueTop, plaqueRight, plaqueBottom, paint)
                
                paint.color = 0xFF4E342E.toInt() // Deep brown
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = 2f
                canvas.drawRect(plaqueLeft + 5f, plaqueTop + 5f, plaqueRight - 5f, plaqueBottom - 5f, paint)
                paint.style = Paint.Style.FILL

                // Text
                paint.typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
                paint.color = 0xFF3E2723.toInt() // Deep brown
                
                // Draw title
                paint.textSize = 28f
                paint.textAlign = Paint.Align.CENTER
                val titleY = 320f
                canvas.drawText(title, widthPt / 2f, titleY, paint)
                
                // Draw separator
                paint.color = 0xFFD4AF37.toInt() // Gold Accent
                canvas.drawRect(widthPt / 2f - 60f, titleY + 30f, widthPt / 2f + 60f, titleY + 32f, paint)
                
                // Draw author
                paint.color = 0xFF5D4037.toInt()
                paint.textSize = 16f
                paint.typeface = Typeface.create(Typeface.SERIF, Typeface.ITALIC)
                canvas.drawText(author, widthPt / 2f, titleY + 90f, paint)
                
                // Vintage footnote
                paint.textSize = 11f
                paint.typeface = Typeface.create(Typeface.SERIF, Typeface.NORMAL)
                canvas.drawText("SPECIAL EDITION • EBOOK DROID PLUS", widthPt / 2f, plaqueBottom - 30f, paint)
            } else if (coverStyle == "Cyberpunk Glow" || coverStyle == "Cyber Punk") {
                // Neon digital block
                paint.color = 0xFF000000.toInt() // black card
                paint.style = Paint.Style.FILL
                canvas.drawRect(50f, 200f, widthPt - 50f, 520f, paint)

                paint.color = 0xFF00E5FF.toInt() // Electric Cyan border
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = 3f
                canvas.drawRect(50f, 200f, widthPt - 50f, 520f, paint)
                paint.style = Paint.Style.FILL

                // Text
                paint.typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
                paint.color = 0xFF00E5FF.toInt()
                paint.textSize = 24f
                paint.textAlign = Paint.Align.CENTER
                val titleY = 320f
                canvas.drawText(title.uppercase(), widthPt / 2f, titleY, paint)

                paint.color = 0xFFFF007F.toInt() // Neon Pink for Author
                paint.textSize = 14f
                canvas.drawText("BY: " + author.uppercase(), widthPt / 2f, titleY + 80f, paint)

                paint.color = 0xFF00FF66.toInt() // Neon Green status
                paint.textSize = 10f
                canvas.drawText("STATUS: LOADED // SEC_01", widthPt / 2f, 480f, paint)
            } else {
                // Modern minimalist / Gradient style
                paint.color = 0xFFFAFAFA.toInt() // Crisp white text
                paint.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
                paint.textSize = 34f
                paint.textAlign = Paint.Align.LEFT
                
                // Text shadow or backdrop shading
                val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = 0xFFFFFFFF.toInt()
                    textSize = 30f
                    typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
                }
                
                val authorPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = android.graphics.Color.parseColor("#E0E0E0")
                    textSize = 16f
                    typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
                }

                canvas.drawText(title, 50f, 300f, textPaint)
                canvas.drawRect(50f, 330f, 150f, 334f, Paint().apply { color = 0xFFFFFFFF.toInt() })
                canvas.drawText("by $author", 50f, 380f, authorPaint)

                // Bottom badge
                val badgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = 0x33FFFFFF
                }
                canvas.drawRoundRect(50f, 720f, 250f, 755f, 15f, 15f, badgePaint)
                
                val lblPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = 0xFFFFFFFF.toInt()
                    textSize = 11f
                    typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
                }
                canvas.drawText("PREMIUM EBOOK", 75f, 742f, lblPaint)
            }

            document.finishPage(pageCover)

            // ==================== PAGE 2: MAIN STORY CONTENT ====================
            // Generate some story pages based on contentPreset
            val parsedStoryText = getPresetContentPages(contentPreset, title, author)
            
            for ((pIdx, pageTextList) in parsedStoryText.withIndex()) {
                val infoPage = PdfDocument.PageInfo.Builder(widthPt, heightPt, pIdx + 1).create()
                val pageObj = document.startPage(infoPage)
                val pCanvas = pageObj.canvas

                // Draw background (soft cream book color)
                val bgPaint = Paint().apply { color = 0xFFFFFDF9.toInt() }
                pCanvas.drawRect(0f, 0f, widthPt.toFloat(), heightPt.toFloat(), bgPaint)

                // Margin lines
                val borderPaint = Paint().apply {
                    style = Paint.Style.STROKE
                    strokeWidth = 1f
                    color = 0xFFEBE8DF.toInt()
                }
                pCanvas.drawRect(35f, 35f, widthPt - 35f, heightPt - 35f, borderPaint)

                // Header
                val headerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = 0xFF757575.toInt()
                    textSize = 10f
                    typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
                    textAlign = Paint.Align.CENTER
                }
                pCanvas.drawText("$title — $author", widthPt / 2f, 24f, headerPaint)

                // Draw story paragraphs
                val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = 0xFF212121.toInt()
                    textSize = 14f
                    typeface = Typeface.create(Typeface.SERIF, Typeface.NORMAL)
                }

                var currentY = 80f
                for (line in pageTextList) {
                    if (line.startsWith("###")) {
                        // Header line
                        val hPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                            color = 0xFF1B5E20.toInt()
                            textSize = 18f
                            typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
                        }
                        currentY += 15f
                        pCanvas.drawText(line.replace("###", "").trim(), 50f, currentY, hPaint)
                        currentY += 25f
                    } else {
                        // Standard body text line
                        pCanvas.drawText(line, 50f, currentY, textPaint)
                        currentY += 24f
                    }
                }

                // Page Number footer
                val footerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = 0xFF757575.toInt()
                    textSize = 10f
                    typeface = Typeface.create(Typeface.SERIF, Typeface.NORMAL)
                    textAlign = Paint.Align.CENTER
                }
                pCanvas.drawText("${pIdx + 1}", widthPt / 2f, heightPt - 20f, footerPaint)

                document.finishPage(pageObj)
            }

            // Save documents
            val fos = FileOutputStream(outputFile)
            document.writeTo(fos)
            fos.close()
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        } finally {
            document.close()
        }
    }

    private fun getPresetContentPages(preset: String, title: String, author: String): List<List<String>> {
        val lists = mutableListOf<List<String>>()
        
        if (preset == "Bangla Literature Sample" || preset.contains("Bangla")) {
            lists.add(listOf(
                "### প্রথম পরিচ্ছেদ — সূচনা ও আবাহন",
                "মহাবিশ্বের অপার আলোছায়ায় মানুষের জীবন এক অদ্ভুত রহস্যে ঘেরা।",
                "লেখক $author-এর গভীর ভাবনায় ফুটে উঠেছে এক অনন্য সামাজিক বাস্তবতার কাব্য।",
                "বেলা গড়িয়ে এসেছে, সূর্যাস্তের দীপ্তিতে রাঙা আকাশ মলিন হতে শুরু করেছে।",
                "নদীর কূলে একা বসে তরুণটি জলের মৃদু লহরী গুনছিল। তার মনের গভীরে আজ",
                "অনেক কথা জমে আছে, যা কখনো প্রকাশ করা হয়নি। বাতাস ছাতিম বনের গন্ধ মেখে",
                "ধীরে ধীরে বইছিল।",
                "",
                "পাখিরা কুলায়ে ফিরছে। গ্রাম বাংলার শান্ত প্রান্তর আজ যেন এক অনাস্বাদিত",
                "নীরবতায় আচ্ছন্ন। এই নীরবতার মধ্য দিয়েই সূচনা হয় এক রোমাঞ্চকর উপাখ্যানের।",
                "যার প্রতিটি পাতা আমাদের মনে করিয়ে দেয় শিকড়ের কথা, আমাদের ভালোবাসার গল্প।"
            ))
            lists.add(listOf(
                "### দ্বিতীয় পরিচ্ছেদ — অজানার অন্বেষণ",
                "পরদিন সকালে নতুন সূর্য উঠতেই সবাই প্রস্তুত হতে শুরু করল।",
                "গ্রামের মেঠো পথ ধরে হেঁটে চলাই ছিল তাদের প্রধান রোমাঞ্চ। সবুজ ধানক্ষেতের",
                "ভেতর দিয়ে বয়ে যাওয়া চমৎকার পথের দুই পাশে বাতাসে সবুজ ঢেউ খেলছিল।",
                "পথের ধারে বড় বটবৃক্ষের তলায় এসে পথিকরা কিছুক্ষণ বিশ্রাম নিল।",
                " can be seen. বৃদ্ধ বটগাছটি যেন বহু যুগের সাক্ষী হয়ে দাঁড়িয়ে আছে নীরব দ্রষ্টা হিসেবে।",
                "তার ঝুরি লতাগুলো ধুলো মাটিকে স্পর্শ করে আদর করছে।",
                "",
                "\"আমরা কি আমাদের গন্তব্যে পৌঁছাতে পারব?\"— তরুণটি মৃদুস্বরে জিজ্ঞেস করল।",
                "উত্তরে তার প্রবীণ সঙ্গী শুধু একটু হাসল, যে হাসির গভীরে ছিল অনাবিল আশ্বাস।",
                "সত্যিই, জীবন কোনো শেষ গন্তব্য নয়, এটি এক পরম যাত্রা।"
            ))
        } else if (preset == "Tech/Programming Guide" || preset.contains("Tech")) {
            lists.add(listOf(
                "### Chapter 1 — The Coding Odyssey",
                "Software development is both an art and a strict science.",
                "In this book, $author covers the foundational patterns of writing clean,",
                "maintainable, and asynchronous Android apps using Jetpack Compose.",
                "Every function you write must be a single unit of truth. The modern",
                "framework provides declarative design, where components react directly",
                "to reactive state bindings like StateFlow or Livedata.",
                "",
                "Kotlin Coroutines and Flow represent the pinnacle of concurrent design.",
                "Instead of blocking the main thread, tasks yield execution safely.",
                "Let's explore how to design modern robust UI layouts with precision."
            ))
            lists.add(listOf(
                "### Chapter 2 — Designing the State Engine",
                "Architecture defines the scalability of a production app.",
                "We implement the Model-View-ViewModel (MVVM) structural pattern.",
                "The ViewModel stores current states and exposes them as immutable flows.",
                "Composables subscribe to these flows using collectAsStateWithLifecycle(),",
                "ensuring that background resources are fully released when inactive.",
                "",
                "By keeping logic separate from rendering code, your application becomes",
                "extremely simple to unit test with Robolectric or mock frameworks.",
                "Remember: Always design with unidirectional data flow (UDF) constraints."
            ))
        } else {
            // General classic fiction sample
            lists.add(listOf(
                "### Part 1 — The Midnight Whisper",
                "The old house stood silent on the crest of the hill under the moon.",
                "It had been abandoned for half a century, yet tonight, a gentle light",
                "sparkled from the attic window. Written by $author, this tale unfolds",
                "the mystery of the forgotten lineage of $title.",
                "The dry leaves swirled playfully as a cold breeze swept through.",
                "In his pocket, Henry held a single antique copper key.",
                "",
                "He took a slow, deep breath. Every step towards the massive oak",
                "door echoed like a drumbeat. The lock clicked, and the adventure",
                "of a lifetime opened its silent doors before him."
            ))
            lists.add(listOf(
                "### Part 2 — Hidden Manuscripts",
                "Dust played in the beams of the flashlight, dancing like static stars.",
                "On the wooden desk lay a leather-bound journal with golden trim.",
                "Henry opened the first page, and his eyes scanned the elegant cursive",
                "script: 'To whomever finds this, beware of what lies beneath.'",
                "The letters were faded but holding an undeniable call to discovery.",
                "",
                "There was a hollow plank in the floor directly beneath the table.",
                "Kneeling, he lifted it carefully, revealing a velvet envelope containing",
                "the ancient plans of the manor and the secret codes to the safe."
            ))
        }
        return lists
    }
}
