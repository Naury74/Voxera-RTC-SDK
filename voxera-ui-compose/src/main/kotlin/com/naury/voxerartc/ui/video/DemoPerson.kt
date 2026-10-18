package com.naury.voxerartc.ui.video

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import kotlin.math.PI
import kotlin.math.absoluteValue
import kotlin.math.sin

/** 데모 인물의 머리 모양. */
internal enum class DemoHair { Short, Long, Bun, Curly, Buzz }

/** 데모 인물 뒤에 보이는 방. */
internal enum class DemoScene { Window, Shelf, Plant, Lamp }

/**
 * 데모 영상 속 인물 한 명의 생김새. 참가자 id로 정해지므로 같은 사람은 늘 같은 모습이다.
 * 실제 사람 사진을 쓰지 않으려고 도형으로 그리는 일러스트이고, 피부색과 머리 모양을 고르게 섞는다.
 *
 * @property phase 눈 깜빡임과 고개 움직임이 사람마다 다른 때에 일어나게 하는 0..1 값
 */
internal data class DemoLook(
    val skin: Color,
    val hair: Color,
    val hairStyle: DemoHair,
    val shirt: Color,
    val wall: Color,
    val scene: DemoScene,
    val glasses: Boolean,
    val phase: Float,
)

/** [participantId]로 생김새를 정한다. 특징마다 다른 수를 곱해 이름이 비슷해도 서로 다른 조합이 나오게 한다. */
internal fun demoLook(participantId: String): DemoLook {
    val h = participantId.fold(7) { acc, c -> acc * 31 + c.code }.absoluteValue
    fun pick(salt: Int, size: Int) = ((h / salt) xor (h * salt)).absoluteValue % size
    val wall = Walls[pick(13, Walls.size)]
    var shirtIndex = pick(11, Shirts.size)
    // 옷이 벽과 비슷한 밝기면 몸이 배경에 묻히므로 다른 옷을 고른다.
    while ((Shirts[shirtIndex].luminance() - wall.luminance()).absoluteValue < 0.18f) shirtIndex = (shirtIndex + 3) % Shirts.size
    return DemoLook(
        skin = Skins[pick(3, Skins.size)],
        hair = Hairs[pick(5, Hairs.size)],
        hairStyle = DemoHair.entries[pick(7, DemoHair.entries.size)],
        shirt = Shirts[shirtIndex],
        wall = wall,
        scene = DemoScene.entries[pick(17, DemoScene.entries.size)],
        glasses = pick(19, 4) == 0,
        phase = pick(23, 100) / 100f,
    )
}

/**
 * [look] 인물을 상반신 화상 통화 구도로 그린다. 세로와 가로 영상 모두 짧은 변을 기준으로 크기를 잡는다.
 *
 * @param time 0..1로 되풀이되는 시간. 눈 깜빡임과 고개 움직임에 쓴다.
 * @param tint 모든 색에 적용할 변환(내 영상의 카메라 효과)
 */
internal fun DrawScope.drawDemoPerson(look: DemoLook, time: Float, tint: (Color) -> Color) {
    val w = size.width
    val h = size.height
    val unit = minOf(w, h)
    val t = (time + look.phase) % 1f
    val bob = sin(t * 2f * PI.toFloat()) * unit * 0.008f
    val sway = sin(t * 4f * PI.toFloat() + 1f) * unit * 0.006f

    drawRect(tint(look.wall))
    drawScene(look, unit, tint)

    val cx = w / 2f + sway
    val headR = minOf(w * 0.21f, h * 0.15f)
    val headCy = h * 0.42f + bob
    val skinShade = lerp(look.skin, Color.Black, 0.16f)
    val shoulderTop = headCy + headR * 1.35f

    // 긴 머리는 얼굴과 어깨 뒤로 내려온다.
    if (look.hairStyle == DemoHair.Long) {
        drawRoundRect(
            tint(look.hair),
            topLeft = Offset(cx - headR * 1.15f, headCy - headR * 0.9f),
            size = Size(headR * 2.3f, headR * 2.9f),
            cornerRadius = CornerRadius(headR, headR),
        )
    }
    // 목과 몸통. 몸통은 아래 끝까지 내려 상반신처럼 보이게 한다.
    drawRect(tint(skinShade), topLeft = Offset(cx - headR * 0.38f, headCy + headR * 0.6f), size = Size(headR * 0.76f, headR * 1.0f))
    val bodyW = headR * 5.2f
    drawRoundRect(
        tint(look.shirt),
        topLeft = Offset(cx - bodyW / 2f, shoulderTop),
        size = Size(bodyW, h - shoulderTop + headR),
        cornerRadius = CornerRadius(headR * 1.1f, headR * 1.1f),
    )
    // 둥근 목둘레
    drawArc(
        tint(skinShade),
        startAngle = 0f,
        sweepAngle = 180f,
        useCenter = true,
        topLeft = Offset(cx - headR * 0.5f, shoulderTop - headR * 0.32f),
        size = Size(headR * 1.0f, headR * 0.7f),
    )
    // 귀, 머리카락, 얼굴 순서로 그린다. 머리카락은 얼굴보다 조금 크게 뒤에 깔아 이마 선이 둥글게 보이게 한다.
    val ear = Size(headR * 0.34f, headR * 0.5f)
    drawOval(tint(skinShade), topLeft = Offset(cx - headR * 0.98f, headCy - ear.height * 0.4f), size = ear)
    drawOval(tint(skinShade), topLeft = Offset(cx + headR * 0.64f, headCy - ear.height * 0.4f), size = ear)
    drawHairBack(look, cx, headCy, headR, tint)
    drawOval(tint(look.skin), topLeft = Offset(cx - headR * 0.82f, headCy - headR * 0.78f), size = Size(headR * 1.64f, headR * 1.84f))
    drawHairFront(look, cx, headCy, headR, tint)
    drawFace(look, cx, headCy, headR, t, skinShade, tint)
}

/** 얼굴 뒤에 까는 머리카락. 얼굴 위와 옆으로 조금씩 보인다. */
private fun DrawScope.drawHairBack(look: DemoLook, cx: Float, cy: Float, r: Float, tint: (Color) -> Color) {
    val hair = tint(look.hair)
    val crown = Offset(cx - r * 0.9f, cy - r * 1.08f)
    val crownSize = Size(r * 1.8f, r * 1.7f)
    when (look.hairStyle) {
        // 짧게 민 머리는 피부가 비치도록 머리색을 피부와 섞는다.
        DemoHair.Buzz -> drawOval(tint(lerp(look.hair, look.skin, 0.45f)), topLeft = Offset(cx - r * 0.86f, cy - r * 0.96f), size = Size(r * 1.72f, r * 1.5f))
        DemoHair.Short, DemoHair.Long -> drawOval(hair, topLeft = crown, size = crownSize)
        DemoHair.Bun -> {
            drawCircle(hair, radius = r * 0.38f, center = Offset(cx, cy - r * 1.22f))
            drawOval(hair, topLeft = crown, size = crownSize)
        }
        DemoHair.Curly -> {
            drawOval(hair, topLeft = crown, size = crownSize)
            for (i in 0..8) {
                val a = PI.toFloat() * (i / 8f)
                drawCircle(hair, radius = r * 0.3f, center = Offset(cx - kotlin.math.cos(a) * r * 0.82f, cy - r * 0.3f - sin(a) * r * 0.78f))
            }
        }
    }
}

/** 이마에 내려오는 앞머리. 한쪽으로 넘긴 모양이다. */
private fun DrawScope.drawHairFront(look: DemoLook, cx: Float, cy: Float, r: Float, tint: (Color) -> Color) {
    if (look.hairStyle != DemoHair.Short && look.hairStyle != DemoHair.Long) return
    drawOval(tint(look.hair), topLeft = Offset(cx - r * 0.78f, cy - r * 0.86f), size = Size(r * 1.05f, r * 0.42f))
}

private fun DrawScope.drawFace(look: DemoLook, cx: Float, cy: Float, r: Float, t: Float, skinShade: Color, tint: (Color) -> Color) {
    val ink = tint(Color(0xFF2A211D))
    // 4초에 한 번쯤 짧게 눈을 감는다.
    val blink = ((t * 3f) % 1f).let { if (it < 0.035f) 0.15f else 1f }
    val eyeY = cy + r * 0.05f
    val eyeDx = r * 0.36f
    val eye = Size(r * 0.17f, r * 0.2f * blink)
    drawOval(ink, topLeft = Offset(cx - eyeDx - eye.width / 2f, eyeY - eye.height / 2f), size = eye)
    drawOval(ink, topLeft = Offset(cx + eyeDx - eye.width / 2f, eyeY - eye.height / 2f), size = eye)
    if (blink == 1f) {
        val glint = tint(Color.White.copy(alpha = 0.85f))
        drawCircle(glint, radius = r * 0.035f, center = Offset(cx - eyeDx + r * 0.03f, eyeY - r * 0.04f))
        drawCircle(glint, radius = r * 0.035f, center = Offset(cx + eyeDx + r * 0.03f, eyeY - r * 0.04f))
    }
    // 눈썹
    val brow = tint(if (look.skin.luminance() < 0.2f) lerp(look.hair, Color.Black, 0.5f) else lerp(look.hair, Color.Black, 0.2f))
    val browY = eyeY - r * 0.3f
    drawLine(brow, Offset(cx - eyeDx - r * 0.15f, browY), Offset(cx - eyeDx + r * 0.15f, browY - r * 0.03f), strokeWidth = r * 0.07f, cap = StrokeCap.Round)
    drawLine(brow, Offset(cx + eyeDx - r * 0.15f, browY - r * 0.03f), Offset(cx + eyeDx + r * 0.15f, browY), strokeWidth = r * 0.07f, cap = StrokeCap.Round)
    if (look.glasses) {
        val frame = Stroke(width = r * 0.06f)
        drawCircle(ink, radius = r * 0.24f, center = Offset(cx - eyeDx, eyeY), style = frame)
        drawCircle(ink, radius = r * 0.24f, center = Offset(cx + eyeDx, eyeY), style = frame)
        drawLine(ink, Offset(cx - eyeDx + r * 0.24f, eyeY), Offset(cx + eyeDx - r * 0.24f, eyeY), strokeWidth = r * 0.05f)
    }
    // 코와 볼, 웃는 입
    drawLine(tint(skinShade), Offset(cx, eyeY + r * 0.12f), Offset(cx - r * 0.06f, eyeY + r * 0.36f), strokeWidth = r * 0.06f, cap = StrokeCap.Round)
    val cheek = tint(lerp(look.skin, Color(0xFFE0626A), 0.18f))
    drawCircle(cheek, radius = r * 0.13f, center = Offset(cx - r * 0.5f, eyeY + r * 0.38f))
    drawCircle(cheek, radius = r * 0.13f, center = Offset(cx + r * 0.5f, eyeY + r * 0.38f))
    val dark = look.skin.luminance() < 0.2f
    drawArc(
        tint(if (dark) Color(0xFFE3A39A) else Color(0xFF7A3B34)),
        startAngle = 20f,
        sweepAngle = 140f,
        useCenter = false,
        topLeft = Offset(cx - r * 0.26f, eyeY + r * 0.3f),
        size = Size(r * 0.52f, r * 0.32f),
        style = Stroke(width = r * 0.07f, cap = StrokeCap.Round),
    )
}

/** 인물 뒤 방. 가로 영상에서는 양옆이 더 보이므로 물건을 화면 가장자리 쪽에 둔다. */
private fun DrawScope.drawScene(look: DemoLook, unit: Float, tint: (Color) -> Color) {
    val w = size.width
    val h = size.height
    val trim = tint(lerp(look.wall, Color.Black, 0.12f))
    when (look.scene) {
        DemoScene.Window -> {
            val frameW = unit * 0.42f
            val frameH = unit * 0.5f
            val left = w * 0.06f
            val top = h * 0.1f
            drawRect(trim, topLeft = Offset(left - unit * 0.02f, top - unit * 0.02f), size = Size(frameW + unit * 0.04f, frameH + unit * 0.04f))
            drawRect(tint(Color(0xFFBFDCEB)), topLeft = Offset(left, top), size = Size(frameW, frameH))
            drawRect(trim, topLeft = Offset(left + frameW / 2f - unit * 0.01f, top), size = Size(unit * 0.02f, frameH))
            drawRect(trim, topLeft = Offset(left, top + frameH / 2f - unit * 0.01f), size = Size(frameW, unit * 0.02f))
        }
        DemoScene.Shelf -> {
            val shelfY = h * 0.3f
            val right = w * 0.97f
            val shelfW = unit * 0.46f
            drawRect(trim, topLeft = Offset(right - shelfW, shelfY), size = Size(shelfW, unit * 0.025f))
            val books = listOf(Color(0xFFB5523B), Color(0xFF3F6E8C), Color(0xFFD4A84A), Color(0xFF5E7F5A), Color(0xFF8A5A83))
            var x = right - shelfW + unit * 0.03f
            books.forEachIndexed { i, c ->
                val bw = unit * (0.045f + (i % 2) * 0.015f)
                val bh = unit * (0.13f + (i % 3) * 0.03f)
                drawRect(tint(c), topLeft = Offset(x, shelfY - bh), size = Size(bw, bh))
                x += bw + unit * 0.008f
            }
        }
        DemoScene.Plant -> {
            val px = maxOf(w * 0.12f, unit * 0.16f)
            val base = h * 0.62f
            val leaf = tint(Color(0xFF5E8F5A))
            for (i in -2..2) {
                drawOval(leaf, topLeft = Offset(px + i * unit * 0.045f - unit * 0.035f, base - unit * (0.24f - (i * i) * 0.025f)), size = Size(unit * 0.07f, unit * 0.2f))
            }
            drawRoundRect(tint(Color(0xFFC07A52)), topLeft = Offset(px - unit * 0.09f, base - unit * 0.06f), size = Size(unit * 0.18f, unit * 0.15f), cornerRadius = CornerRadius(unit * 0.02f))
        }
        DemoScene.Lamp -> {
            val lx = w * 0.84f
            drawLine(trim, Offset(lx, 0f), Offset(lx, h * 0.14f), strokeWidth = unit * 0.008f)
            drawArc(tint(Color(0xFFE7B95C)), 180f, 180f, true, topLeft = Offset(lx - unit * 0.1f, h * 0.14f), size = Size(unit * 0.2f, unit * 0.16f))
            drawCircle(tint(Color(0xFFFFF1C9)), radius = unit * 0.03f, center = Offset(lx, h * 0.14f + unit * 0.08f))
        }
    }
}

private val Skins = listOf(
    Color(0xFFF6D5BE), Color(0xFFEBBE9B), Color(0xFFD29C75), Color(0xFFAE7651), Color(0xFF85573A), Color(0xFF5E3C29),
)
private val Hairs = listOf(
    Color(0xFF2B1F1A), Color(0xFF4A3426), Color(0xFF7A5232), Color(0xFFC9A36B), Color(0xFF8E8E92), Color(0xFF1E1E24), Color(0xFFA2502E),
)
private val Shirts = listOf(
    Color(0xFF2F7F77), Color(0xFF2E3D5C), Color(0xFFC9A13B), Color(0xFFD9735E), Color(0xFF8FA88A), Color(0xFF6E4C6E), Color(0xFFE4E7EA), Color(0xFF3A3F47),
)
private val Walls = listOf(
    Color(0xFFE7E1D7), Color(0xFFD7E2E4), Color(0xFFEADCCE), Color(0xFFD0DAD3), Color(0xFFDED7E5), Color(0xFFE3E6EA),
)
