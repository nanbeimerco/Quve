package com.superglobalpeq.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

val QuveAccentOrange = Color(0xFFFF5935)

private val QuveGlyphContours: List<List<Pair<Float, Float>>> = listOf(
        listOf(820f to 371f, 719f to 371f, 719f to 590f, 718f to 591f, 718f to 598f, 717f to 599f, 717f to 603f, 716f to 604f, 715f to 611f, 713f to 614f, 713f to 616f, 712f to 617f, 712f to 619f, 710f to 623f, 708f to 625f, 707f to 628f, 701f to 635f, 700f to 635f, 695f to 640f, 688f to 643f, 687f to 645f, 688f to 646f, 688f to 648f, 697f to 666f, 699f to 668f, 699f to 669f, 701f to 671f, 703f to 675f, 712f to 685f, 712f to 686f, 727f to 701f, 728f to 701f, 732f to 705f, 733f to 705f, 736f to 708f, 737f to 708f, 741f to 712f, 742f to 712f, 753f to 720f, 755f to 720f, 757f to 719f, 759f to 717f, 760f to 717f, 766f to 711f, 767f to 711f, 785f to 692f, 785f to 691f, 787f to 689f, 789f to 685f, 794f to 679f, 799f to 668f, 802f to 664f, 802f to 662f, 805f to 657f, 805f to 655f, 809f to 647f, 809f to 644f, 810f to 643f, 810f to 641f, 811f to 640f, 811f to 637f, 812f to 636f, 812f to 634f, 813f to 633f, 813f to 630f, 814f to 629f, 814f to 625f, 815f to 624f, 816f to 615f, 817f to 614f, 817f to 607f, 818f to 606f, 818f to 599f, 819f to 598f, 819f to 580f, 820f to 579f),
        listOf(203f to 371f, 203f to 579f, 204f to 580f, 204f to 598f, 205f to 599f, 205f to 606f, 206f to 607f, 206f to 614f, 207f to 615f, 207f to 619f, 208f to 620f, 209f to 629f, 210f to 630f, 210f to 633f, 211f to 634f, 211f to 636f, 212f to 637f, 212f to 640f, 214f to 644f, 214f to 647f, 216f to 650f, 216f to 652f, 218f to 655f, 218f to 657f, 221f to 662f, 221f to 664f, 224f to 668f, 229f to 679f, 234f to 685f, 234f to 686f, 236f to 688f, 238f to 692f, 243f to 697f, 243f to 698f, 263f to 717f, 267f to 719f, 270f to 722f, 271f to 722f, 279f to 728f, 295f to 736f, 297f to 736f, 302f to 739f, 304f to 739f, 307f to 741f, 309f to 741f, 310f to 742f, 313f to 742f, 317f to 744f, 320f to 744f, 324f to 746f, 328f to 746f, 329f to 747f, 333f to 747f, 334f to 748f, 338f to 748f, 339f to 749f, 344f to 749f, 345f to 750f, 352f to 750f, 353f to 751f, 361f to 751f, 362f to 752f, 555f to 752f, 553f to 750f, 552f to 747f, 550f to 745f, 544f to 732f, 542f to 730f, 541f to 728f, 541f to 726f, 537f to 719f, 537f to 717f, 531f to 705f, 531f to 703f, 530f to 702f, 530f to 700f, 529f to 699f, 528f to 694f, 526f to 691f, 526f to 689f, 524f to 685f, 523f to 678f, 522f to 677f, 522f to 675f, 521f to 674f, 521f to 671f, 520f to 670f, 520f to 667f, 518f to 663f, 518f to 658f, 517f to 657f, 517f to 652f, 376f to 652f, 375f to 651f, 363f to 651f, 362f to 650f, 356f to 650f, 355f to 649f, 346f to 648f, 340f to 645f, 338f to 645f, 328f to 640f, 320f to 633f, 320f to 632f, 316f to 628f, 315f to 625f, 313f to 623f, 311f to 619f, 310f to 614f, 308f to 611f, 308f to 608f, 307f to 607f, 307f to 604f, 306f to 603f, 306f to 599f, 305f to 598f, 305f to 591f, 304f to 590f, 304f to 371f),
        listOf(544f to 186f, 544f to 598f, 545f to 599f, 545f to 618f, 546f to 619f, 546f to 627f, 547f to 628f, 547f to 636f, 548f to 637f, 548f to 641f, 549f to 642f, 549f to 647f, 550f to 648f, 551f to 657f, 552f to 658f, 552f to 661f, 553f to 662f, 553f to 665f, 554f to 666f, 554f to 669f, 556f to 673f, 556f to 676f, 557f to 677f, 558f to 682f, 560f to 685f, 560f to 687f, 561f to 688f, 562f to 693f, 567f to 703f, 567f to 705f, 571f to 712f, 571f to 714f, 574f to 718f, 579f to 729f, 581f to 731f, 582f to 734f, 586f to 739f, 587f to 742f, 589f to 744f, 589f to 745f, 591f to 747f, 593f to 751f, 602f to 762f, 602f to 763f, 606f to 767f, 606f to 768f, 614f to 776f, 614f to 777f, 635f to 798f, 636f to 798f, 648f to 809f, 649f to 809f, 653f to 813f, 654f to 813f, 665f to 822f, 666f to 822f, 668f to 824f, 669f to 824f, 671f to 826f, 672f to 826f, 674f to 828f, 675f to 828f, 686f to 836f, 688f to 836f, 690f to 832f, 694f to 827f, 695f to 824f, 697f to 822f, 698f to 819f, 702f to 814f, 703f to 811f, 705f to 809f, 709f to 801f, 713f to 796f, 717f to 788f, 721f to 783f, 725f to 775f, 729f to 770f, 733f to 762f, 737f to 757f, 740f to 751f, 736f to 748f, 735f to 748f, 733f to 746f, 732f to 746f, 730f to 744f, 729f to 744f, 727f to 742f, 726f to 742f, 723f to 739f, 722f to 739f, 719f to 736f, 718f to 736f, 715f to 733f, 714f to 733f, 711f to 730f, 710f to 730f, 702f to 722f, 701f to 722f, 683f to 703f, 683f to 702f, 676f to 694f, 676f to 693f, 668f to 682f, 666f to 677f, 664f to 675f, 659f to 665f, 659f to 663f, 657f to 660f, 657f to 658f, 655f to 655f, 655f to 653f, 654f to 652f, 654f to 650f, 653f to 649f, 653f to 647f, 651f to 643f, 651f to 640f, 650f to 639f, 650f to 636f, 649f to 635f, 649f to 631f, 648f to 630f, 648f to 626f, 647f to 625f, 647f to 618f, 646f to 617f, 646f to 610f, 645f to 609f, 645f to 186f),
        listOf(378f to 186f, 378f to 572f, 479f to 572f, 479f to 186f)
)

@Composable
fun QuveGlyphMark(
    modifier: Modifier = Modifier,
    tint: Color = QuveAccentOrange,
) {
    Canvas(modifier = modifier) {
        val scale = minOf(size.width, size.height) / 1024f
        val ox = (size.width - 1024f * scale) * 0.5f
        val oy = (size.height - 1024f * scale) * 0.5f
        for (contour in QuveGlyphContours) {
            if (contour.isEmpty()) continue
            val path = Path().apply {
                moveTo(ox + contour[0].first * scale, oy + contour[0].second * scale)
                for (i in 1 until contour.size) {
                    lineTo(ox + contour[i].first * scale, oy + contour[i].second * scale)
                }
                close()
            }
            drawPath(path = path, color = tint)
        }
    }
}

@Composable
fun QuveAppIconTile(
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
    backgroundColor: Color = Color.Black,
    glyphColor: Color = QuveAccentOrange,
    shape: Shape = RoundedCornerShape(13.dp),
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(shape)
            .background(backgroundColor),
        contentAlignment = Alignment.Center,
    ) {
        QuveGlyphMark(modifier = Modifier.fillMaxSize(), tint = glyphColor)
    }
}
