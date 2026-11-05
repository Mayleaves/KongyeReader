// 生成：tools/import_achievements.py（来自成就设计 HTML 的 renderCenterGraphic 和 FACTIONS）
package com.kongye.reader.ui.components

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform

/** 成就页的配色方案：第一套免费，其余用叶子换 */
data class BadgeStyle(
    val id: String, val name: String, val code: String,
    val pri: Color, val sec: Color, val acc: Color, val plate: Color, val sub: Color,
    val paper: Color, val card: Color, val lockProg: Color,
)

val badgeStyles = listOf(
    BadgeStyle("classic", "原版黑金", "CLASSIC // ARCHIVE-01", Color(0xFFD89B3C), Color(0xFFB86B35), Color(0xFFF7D279), Color(0xFF17171B), Color(0xFF23242B), Color(0xFFFAF8F5), Color(0xFF1D1D22), Color(0xFF9A7438)),
    BadgeStyle("nightcity", "2077 夜之城", "NC-2077 // MERCENARY", Color(0xFFFCEE09), Color(0xFF00F0FF), Color(0xFFFF003C), Color(0xFF111318), Color(0xFF1D212A), Color(0xFFFFFFFF), Color(0xFF12141B), Color(0xFF00A8B5)),
    BadgeStyle("arasaka", "荒坂集团", "ARASAKA // CORP-SEC", Color(0xFFFF1E27), Color(0xFFD4D4D8), Color(0xFFFF5258), Color(0xFF0E0E11), Color(0xFF1C1C21), Color(0xFFFFFFFF), Color(0xFF111115), Color(0xFFC81E25)),
    BadgeStyle("militech", "军用科技", "MILITECH // TAC-OPS", Color(0xFFF5A623), Color(0xFF6E825B), Color(0xFFD4E157), Color(0xFF181C16), Color(0xFF242B22), Color(0xFFF4F6F0), Color(0xFF1A1F18), Color(0xFF6E825B)),
    BadgeStyle("kangtao", "康陶重工", "KANG TAO // SMART-SYS", Color(0xFFFF6B00), Color(0xFF00B8D4), Color(0xFFFFD166), Color(0xFF101820), Color(0xFF1B2836), Color(0xFFFFFFFF), Color(0xFF111B24), Color(0xFF0097B2)),
    BadgeStyle("trauma", "创伤小组", "TRAUMA TEAM // PLATINUM", Color(0xFF00E676), Color(0xFFFF2A4B), Color(0xFFFFFFFF), Color(0xFF101E1A), Color(0xFF1A2E28), Color(0xFFFFFFFF), Color(0xFF11211C), Color(0xFF00B85C)),
    BadgeStyle("mox", "莫克斯帮", "THE MOX // LIZZIE-BAR", Color(0xFFFF2A85), Color(0xFF00F0FF), Color(0xFFFFE600), Color(0xFF1E1024), Color(0xFF2D1936), Color(0xFFFFFFFF), Color(0xFF201126), Color(0xFFD91C6B)),
    BadgeStyle("tyger", "虎爪帮", "TYGER CLAWS // JAPANTON", Color(0xFF00E5A3), Color(0xFFFF2E4C), Color(0xFFFFD54F), Color(0xFF111B18), Color(0xFF1C2B27), Color(0xFFFFFFFF), Color(0xFF131E1B), Color(0xFF00B37E)),
)

private object BadgePaths {
    val p0 by lazy { svgPath("M24 33 L42 37 L42 60 L24 55 Z") }
    val p1 by lazy { svgPath("M64 33 L46 37 L46 60 L64 55 Z") }
    val p2 by lazy { svgPath("M24 58 L44 63 L64 58 L64 61 L44 67 L24 61 Z") }
    val p3 by lazy { svgPath("M28 41 L38 43.5") }
    val p4 by lazy { svgPath("M28 47 L38 49.5") }
    val p5 by lazy { svgPath("M50 43.5 L60 41") }
    val p6 by lazy { svgPath("M50 49.5 L60 47") }
    val p7 by lazy { svgPath("M44 22 L47 29 L41 29 Z") }
    val p8 by lazy { svgPath("M35 24 L53 24 L64 35 L64 53 L53 64 L35 64 L24 53 L24 35 Z") }
    val p9 by lazy { svgPath("M44 24 L53 24 L64 35 L64 44 L44 44 Z") }
    val p10 by lazy { svgPath("M44 31 L44 44 L55 44") }
    val p11 by lazy { svgPath("M41 41h6v6h-6Z") }
    val p12 by lazy { svgPath("M25 44a19 19 0 1 0 38 0a19 19 0 1 0 -38 0Z") }
    val p13 by lazy { svgPath("M31 29 L37 29 L57 59 L51 59 Z") }
    val p14 by lazy { svgPath("M57 29 L51 29 L31 59 L37 59 Z") }
    val p15 by lazy { svgPath("M40 40h8v8h-8Z") }
    val p16 by lazy { svgPath("M42 42h4v4h-4Z") }
    val p17 by lazy { svgPath("M27 23h34v3.5h-34Z") }
    val p18 by lazy { svgPath("M27 61.5h34v3.5h-34Z") }
    val p19 by lazy { svgPath("M29 26.5 L59 26.5 L47 43 L41 43 Z") }
    val p20 by lazy { svgPath("M44 26.5 L59 26.5 L47 43 L44 43 Z") }
    val p21 by lazy { svgPath("M41 45 L47 45 L59 61.5 L29 61.5 Z") }
    val p22 by lazy { svgPath("M44 39 L49 44 L44 49 L39 44 Z") }
    val p23 by lazy { svgPath("M26 43 L29 25 L38 34 L44 21 L50 34 L59 25 L62 43 Z") }
    val p24 by lazy { svgPath("M44 21 L50 34 L59 25 L62 43 L44 43 Z") }
    val p25 by lazy { svgPath("M25 47 L42 51 L42 64 L25 60 Z") }
    val p26 by lazy { svgPath("M63 47 L46 51 L46 64 L63 60 Z") }
    val p27 by lazy { svgPath("M60 24 L33 31 L25 58 L52 53 Z") }
    val p28 by lazy { svgPath("M60 24 L52 53 L25 58 Z") }
    val p29 by lazy { svgPath("M25 58 L57 27") }
    val p30 by lazy { svgPath("M52 56 L64 52 L60 64 Z") }
    val p31 by lazy { svgPath("M44 19 L47 26 L41 26 Z") }
    val p32 by lazy { svgPath("M44 69 L47 62 L41 62 Z") }
    val p33 by lazy { svgPath("M19 44 L26 47 L26 41 Z") }
    val p34 by lazy { svgPath("M69 44 L62 47 L62 41 Z") }
    val p35 by lazy { svgPath("M36 29 L52 29 L59 36 L59 52 L52 59 L36 59 L29 52 L29 36 Z") }
    val p36 by lazy { svgPath("M44 29 L52 29 L59 36 L59 52 L52 59 L44 59 Z") }
    val p37 by lazy { svgPath("M44 37 L51 44 L44 51 L37 44 Z") }
    val p38 by lazy { svgPath("M25 34 L25 25 L34 25") }
    val p39 by lazy { svgPath("M63 34 L63 25 L54 25") }
    val p40 by lazy { svgPath("M25 54 L25 63 L34 63") }
    val p41 by lazy { svgPath("M63 54 L63 63 L54 63") }
    val p42 by lazy { svgPath("M37 31 L51 31 L57 37 L57 51 L51 57 L37 57 L31 51 L31 37 Z") }
    val p43 by lazy { svgPath("M44 38 L50 44 L44 50 L38 44 Z") }
    val p44 by lazy { svgPath("M22 60 L66 60") }
    val p45 by lazy { svgPath("M27 65 L61 65") }
    val p46 by lazy { svgPath("M49,22 C34,23 25,35 28,49 C30,61 43,67 56,62 C45,62 37,53 37,42 C37,31 43,25 49,22 Z") }
    val p47 by lazy { svgPath("M56 31 L59 38 L66 41 L59 44 L56 51 L53 44 L46 41 L53 38 Z") }
    val p48 by lazy { svgPath("M56 38 L58 41 L56 44 L54 41 Z") }
    val p49 by lazy { svgPath("M29 57 L36 44 L52 44 L59 57 Z") }
    val p50 by lazy { svgPath("M21 57 L67 57") }
    val p51 by lazy { svgPath("M23 38 L42 33 L39 45 Z") }
    val p52 by lazy { svgPath("M42 33 L66 24 L49 43 L39 45 Z") }
    val p53 by lazy { svgPath("M42 33 L52 38 L44 50 Z") }
    val p54 by lazy { svgPath("M44 20 L59 40 L54 58 L44 63 L33 58 L29 42 L37 37 L36 45 L44 32 Z") }
    val p55 by lazy { svgPath("M44 20 L59 40 L54 58 L44 63 Z") }
    val p56 by lazy { svgPath("M44 41 L50 50 L44 59 L38 50 Z") }
    val p57 by lazy { svgPath("M27 65h9v3h-9Z") }
    val p58 by lazy { svgPath("M39.5 65h9v3h-9Z") }
    val p59 by lazy { svgPath("M52 65h9v3h-9Z") }
    val p60 by lazy { svgPath("M22 38 L30 31 L30 52 L22 45 Z") }
    val p61 by lazy { svgPath("M66 38 L58 31 L58 52 L66 45 Z") }
    val p62 by lazy { svgPath("M44 19 L57 39 L52 57 L36 57 L31 39 Z") }
    val p63 by lazy { svgPath("M44 19 L57 39 L52 57 L44 57 Z") }
    val p64 by lazy { svgPath("M44 34 L50 44 L44 53 L38 44 Z") }
    val p65 by lazy { svgPath("M24 61h40v4h-40Z") }
    val p66 by lazy { svgPath("M24,47 C26,62 41,69 56,63 C63,60 66,54 66,48 C60,55 49,58 39,55 C32,52 27,49 24,47 Z") }
    val p67 by lazy { svgPath("M44 20 L55 36 L50 50 L38 50 L33 36 Z") }
    val p68 by lazy { svgPath("M44 20 L55 36 L50 50 L44 50 Z") }
    val p69 by lazy { svgPath("M44 31 L48 39 L44 46 L40 39 Z") }
    val p70 by lazy { svgPath("M25 24 L36 34 L52 34 L63 24 L65 52 L53 64 L35 64 L23 52 Z") }
    val p71 by lazy { svgPath("M44 34 L52 34 L63 24 L65 52 L53 64 L44 64 Z") }
    val p72 by lazy { svgPath("M28 30 L34 35 L28 39 Z") }
    val p73 by lazy { svgPath("M60 30 L54 35 L60 39 Z") }
    val p74 by lazy { svgPath("M31 44 L40 44 L38 49 L31 47 Z") }
    val p75 by lazy { svgPath("M57 44 L48 44 L50 49 L57 47 Z") }
    val p76 by lazy { svgPath("M41 53 L47 53 L44 57 Z") }
    val p77 by lazy { svgPath("M32 22 L56 22 L56 66 L44 55 L32 66 Z") }
    val p78 by lazy { svgPath("M44 22 L56 22 L56 66 L44 55 Z") }
    val p79 by lazy { svgPath("M41.5 30h5v16h-5Z") }
    val p80 by lazy { svgPath("M38 30h12v3h-12Z") }
    val p81 by lazy { svgPath("M38 43h12v3h-12Z") }
    val p82 by lazy { svgPath("M24 29h8v31h-8Z") }
    val p83 by lazy { svgPath("M34 24h9v36h-9Z") }
    val p84 by lazy { svgPath("M45 31h8v29h-8Z") }
    val p85 by lazy { svgPath("M55 31 L62 29 L67 58 L60 60 Z") }
    val p86 by lazy { svgPath("M21 62h46v4h-46Z") }
    val p87 by lazy { svgPath("M27 24 L57 24 L60 27 L60 64 L27 64 Z") }
    val p88 by lazy { svgPath("M27 24h6v40h-6Z") }
    val p89 by lazy { svgPath("M32 45 L43 56 L67 29 L61 23 L43 43 L37 38 Z") }
    val p90 by lazy { svgPath("M43 56 L67 29 L61 23 L43 43 Z") }
    val p91 by lazy { svgPath("M44 19 L51 35 L68 37 L55 49 L59 66 L44 57 L29 66 L33 49 L20 37 L37 35 Z") }
    val p92 by lazy { svgPath("M44 19 L51 35 L68 37 L55 49 L59 66 L44 57 Z") }
    val p93 by lazy { svgPath("M39 40 L44 51 L49 40") }
    val p94 by lazy { svgPath("M24 48 L28 27 L38 38 L44 22 L50 38 L60 27 L64 48 Z") }
    val p95 by lazy { svgPath("M44 22 L50 38 L60 27 L64 48 L44 48 Z") }
    val p96 by lazy { svgPath("M24 53h40v9h-40Z") }
    val p97 by lazy { svgPath("M32 53 L32 62") }
    val p98 by lazy { svgPath("M40 53 L40 62") }
    val p99 by lazy { svgPath("M48 53 L48 62") }
    val p100 by lazy { svgPath("M56 53 L56 62") }
    val p101 by lazy { svgPath("M36 45 L52 45 L58 54 L52 62 L36 62 L30 54 Z") }
    val p102 by lazy { svgPath("M44 45 L52 45 L58 54 L52 62 L44 62 Z") }
    val p103 by lazy { svgPath("M44 50 L50 58 L38 58 Z") }
    val p104 by lazy { svgPath("M28 34 L34 31 L36 39 L30 42 Z") }
    val p105 by lazy { svgPath("M38 27 L44 25 L44 35 L38 37 Z") }
    val p106 by lazy { svgPath("M50 27 L44 25 L44 35 L50 37 Z") }
    val p107 by lazy { svgPath("M60 34 L54 31 L52 39 L58 42 Z") }
    val p108 by lazy { svgPath("M20 25 L29 16 L34 16 L20 30 Z") }
    val p109 by lazy { svgPath("M36 46 L52 46 L57 55 L52 62 L36 62 L31 55 Z") }
    val p110 by lazy { svgPath("M44 46 L52 46 L57 55 L52 62 L44 62 Z") }
    val p111 by lazy { svgPath("M28 35 L34 32 L36 40 L30 42 Z") }
    val p112 by lazy { svgPath("M38 28 L43 26 L44 35 L39 37 Z") }
    val p113 by lazy { svgPath("M50 28 L45 26 L44 35 L49 37 Z") }
    val p114 by lazy { svgPath("M60 35 L54 32 L52 40 L58 42 Z") }
    val p115 by lazy { svgPath("M44 18 L47 24 L41 24 Z") }
    val p116 by lazy { svgPath("M23 57 L26 33 L37 43 L44 28 L51 43 L62 33 L65 57 Z") }
    val p117 by lazy { svgPath("M44 28 L51 43 L62 33 L65 57 L44 57 Z") }
    val p118 by lazy { svgPath("M44 40 L49 47 L44 54 L39 47 Z") }
    val p119 by lazy { svgPath("M25 60h38v4.5h-38Z") }
    val p120 by lazy { svgPath("M16 53 L72 53") }
    val p121 by lazy { svgPath("M42 35h4v30h-4Z") }
    val p122 by lazy { svgPath("M44 45 L50 28 L66 26 L60 43 Z") }
    val p123 by lazy { svgPath("M44 45 L66 26 L60 43 Z") }
    val p124 by lazy { svgPath("M44 50 L38 36 L23 34 L29 48 Z") }
    val p125 by lazy { svgPath("M40 28 L44 23 L50 29 L44 34 L44 39") }
    val p126 by lazy { svgPath("M44 37 L20 56 L68 56 Z") }
    val p127 by lazy { svgPath("M44 37 L68 56 L44 56 Z") }
    val p128 by lazy { svgPath("M44 43 L31 52 L57 52 Z") }
    val p129 by lazy { svgPath("M27 59 L61 59 L56 65 L32 65 Z") }
    val p130 by lazy { svgPath("M40 26 L44 22 L50 27 L44 32 L44 37") }
    val p131 by lazy { svgPath("M44 35 L20 53 L68 53 Z") }
    val p132 by lazy { svgPath("M44 35 L68 53 L44 53 Z") }
    val p133 by lazy { svgPath("M44 41 L31 49 L57 49 Z") }
    val p134 by lazy { svgPath("M30 57 L44 62 L30 67 Z") }
    val p135 by lazy { svgPath("M58 57 L44 62 L58 67 Z") }
    val p136 by lazy { svgPath("M44 58 L48 62 L44 66 L40 62 Z") }
    val p137 by lazy { svgPath("M26 23 L36 33 L52 33 L62 23 L62 48 L52 60 L36 60 L26 48 Z") }
    val p138 by lazy { svgPath("M44 33 L52 33 L62 23 L62 48 L52 60 L44 60 Z") }
    val p139 by lazy { svgPath("M24 39 L64 39 L60 47 L46 47 L44 44 L42 47 L28 47 Z") }
    val p140 by lazy { svgPath("M28 41 L42 41 L40 45 L30 45 Z") }
    val p141 by lazy { svgPath("M46 41 L60 41 L58 45 L48 45 Z") }
    val p142 by lazy { svgPath("M33 61h22v3.5h-22Z") }
    val p143 by lazy { svgPath("M44 62 L48 66 L44 70 L40 66 Z") }
    val p144 by lazy { svgPath("M23 21 L38 35 L50 35 L65 21 L61 49 L44 65 L27 49 Z") }
    val p145 by lazy { svgPath("M44 35 L50 35 L65 21 L61 49 L44 65 Z") }
    val p146 by lazy { svgPath("M27 49 L39 49 L44 65 Z") }
    val p147 by lazy { svgPath("M61 49 L49 49 L44 65 Z") }
    val p148 by lazy { svgPath("M33 42 L40 45 L34 47 Z") }
    val p149 by lazy { svgPath("M55 42 L48 45 L54 47 Z") }
    val p150 by lazy { svgPath("M44 28 L47 34 L44 40 L41 34 Z") }
    val p151 by lazy { svgPath("M24 25 L36 31 L52 31 L64 25 L60 37 L62 55 L44 66 L26 55 L28 37 Z") }
    val p152 by lazy { svgPath("M44 31 L52 31 L64 25 L60 37 L62 55 L44 66 Z") }
    val p153 by lazy { svgPath("M29 38 L41 38 L41 48 L29 48 Z") }
    val p154 by lazy { svgPath("M47 38 L59 38 L59 48 L47 48 Z") }
    val p155 by lazy { svgPath("M33 41h4v4h-4Z") }
    val p156 by lazy { svgPath("M51 41h4v4h-4Z") }
    val p157 by lazy { svgPath("M41 49 L47 49 L44 58 Z") }
    val p158 by lazy { svgPath("M48 18 L59 52 L29 52 L38 31 Z") }
    val p159 by lazy { svgPath("M48 18 L59 52 L44 52 Z") }
    val p160 by lazy { svgPath("M28 48h32v5h-32Z") }
    val p161 by lazy { svgPath("M44 46 L48 50 L44 54 L40 50 Z") }
    val p162 by lazy { svgPath("M18 53 L70 53 L63 61 L25 61 Z") }
    val p163 by lazy { svgPath("M44 53 L70 53 L63 61 L44 61 Z") }
    val p164 by lazy { svgPath("M21 36 L26 31 L37 31 L41 36 L41 47 L37 52 L26 52 L21 47 Z") }
    val p165 by lazy { svgPath("M47 36 L51 31 L62 31 L67 36 L67 47 L62 52 L51 52 L47 47 Z") }
    val p166 by lazy { svgPath("M41 39h6v3.5h-6Z") }
    val p167 by lazy { svgPath("M26 35 L31 35 L27 48 L23 48 Z") }
    val p168 by lazy { svgPath("M52 35 L57 35 L53 48 L49 48 Z") }
    val p169 by lazy { svgPath("M21 31 L41 41 L41 47 L21 57 Z") }
    val p170 by lazy { svgPath("M67 31 L47 41 L47 47 L67 57 Z") }
    val p171 by lazy { svgPath("M25 36 L35 41 L25 46 Z") }
    val p172 by lazy { svgPath("M63 36 L53 41 L63 46 Z") }
    val p173 by lazy { svgPath("M39 38 L49 38 L51 44 L49 50 L39 50 L37 44 Z") }
    val p174 by lazy { svgPath("M41.5 41.5h5v5h-5Z") }
    val p175 by lazy { svgPath("M26 24 L62 24 L56 33 L32 33 Z") }
    val p176 by lazy { svgPath("M32 33 L56 33 L66 64 L44 58 L22 64 Z") }
    val p177 by lazy { svgPath("M44 33 L56 33 L66 64 L44 58 Z") }
    val p178 by lazy { svgPath("M44 26 L49 31 L44 36 L39 31 Z") }
    val p179 by lazy { svgPath("M44 28 L47 31 L44 34 L41 31 Z") }
    val p180 by lazy { svgPath("M23 23h42v42h-42Z") }
    val p181 by lazy { svgPath("M44 23 L44 65") }
    val p182 by lazy { svgPath("M23 44 L65 44") }
    val p183 by lazy { svgPath("M33 28 L28 38") }
    val p184 by lazy { svgPath("M57 28 L52 38") }
    val p185 by lazy { svgPath("M36 49 L31 59") }
    val p186 by lazy { svgPath("M52 58 L58 50 L61 58 Z") }
    val p187 by lazy { svgPath("M25 63 L25 34 L35 23 L53 23 L63 34 L63 63 Z") }
    val p188 by lazy { svgPath("M38 36a6 6 0 1 0 12 0a6 6 0 1 0 -12 0Z") }
    val p189 by lazy { svgPath("M44 43 L28 61 L60 61 Z") }
    val p190 by lazy { svgPath("M27 53h6v9h-6Z") }
    val p191 by lazy { svgPath("M55 51h6v11h-6Z") }
    val p192 by lazy { svgPath("M33 27 L46 56 L20 56 Z") }
    val p193 by lazy { svgPath("M52 22 L67 58 L37 58 Z") }
    val p194 by lazy { svgPath("M52 22 L67 58 L52 58 Z") }
    val p195 by lazy { svgPath("M31 56h4v7h-4Z") }
    val p196 by lazy { svgPath("M50 58h4v6h-4Z") }
    val p197 by lazy { svgPath("M28 22 L31 25 L28 28 L25 25 Z") }
    val p198 by lazy { svgPath("M18 60 L70 48") }
    val p199 by lazy { svgPath("M20 64 L70 52") }
    val p200 by lazy { svgPath("M23 44 L59 36 L66 45 L23 55 Z") }
    val p201 by lazy { svgPath("M42 40 L59 36 L66 45 L42 50 Z") }
    val p202 by lazy { svgPath("M28 44h6v4h-6Z") }
    val p203 by lazy { svgPath("M38 42h6v4h-6Z") }
    val p204 by lazy { svgPath("M31 24 L34 27 L31 30 L28 27 Z") }
    val p205 by lazy { svgPath("M54 21 L56 24 L54 27 L52 24 Z") }
    val p206 by lazy { svgPath("M44 18 L44 26") }
    val p207 by lazy { svgPath("M31 26 L57 26 L61 42 L27 42 Z") }
    val p208 by lazy { svgPath("M44 26 L57 26 L61 42 L44 42 Z") }
    val p209 by lazy { svgPath("M44 42 L44 50") }
    val p210 by lazy { svgPath("M44 49 L51 64 L44 68 L38 61 Z") }
    val p211 by lazy { svgPath("M32 20 L56 20 L60 44 L28 44 Z") }
    val p212 by lazy { svgPath("M37 25h14v14h-14Z") }
    val p213 by lazy { svgPath("M44 28 L44 32 L48 32") }
    val p214 by lazy { svgPath("M44 44 L44 56") }
    val p215 by lazy { svgPath("M44 53 L52 60 L44 67 L36 60 Z") }
    val p216 by lazy { svgPath("M44 17 L44 28") }
    val p217 by lazy { svgPath("M34 28 L54 28 L65 44 L23 44 Z") }
    val p218 by lazy { svgPath("M44 28 L54 28 L65 44 L44 44 Z") }
    val p219 by lazy { svgPath("M38 44 L50 44 L47 51 L41 51 Z") }
    val p220 by lazy { svgPath("M44 54 L44 64") }
    val p221 by lazy { svgPath("M33 52 L27 61") }
    val p222 by lazy { svgPath("M55 52 L61 61") }
    val p223 by lazy { svgPath("M24 24 L64 24") }
    val p224 by lazy { svgPath("M30 24 L30 42") }
    val p225 by lazy { svgPath("M44 24 L44 34") }
    val p226 by lazy { svgPath("M58 24 L58 46") }
    val p227 by lazy { svgPath("M30 41 L35 48 L30 55 L25 48 Z") }
    val p228 by lazy { svgPath("M44 33 L51 42 L44 51 L37 42 Z") }
    val p229 by lazy { svgPath("M58 45 L63 52 L58 59 L53 52 Z") }
    val p230 by lazy { svgPath("M32 23 L48 23 L56 31 L56 47 L48 55 L32 55 L24 47 L24 31 Z") }
    val p231 by lazy { svgPath("M50 51 L55 46 L67 58 L62 63 Z") }
    val p232 by lazy { svgPath("M40 30 L46 47 L42 47 L40 39 L38 47 L34 47 Z") }
    val p233 by lazy { svgPath("M20 32 L68 32") }
    val p234 by lazy { svgPath("M20 56 L68 56") }
    val p235 by lazy { svgPath("M41 24 L57 40 L41 56 L25 40 Z") }
    val p236 by lazy { svgPath("M41 30 L51 40 L41 50 L31 40 Z") }
    val p237 by lazy { svgPath("M41 36 L45 40 L41 44 L37 40 Z") }
    val p238 by lazy { svgPath("M51 50 L56 45 L67 56 L62 61 Z") }
    val p239 by lazy { svgPath("M44 19 L59 41 L52 58 L36 58 L29 41 Z") }
    val p240 by lazy { svgPath("M44 19 L59 41 L52 58 L44 58 Z") }
    val p241 by lazy { svgPath("M44 19 L44 42") }
    val p242 by lazy { svgPath("M44 39 L48 43 L44 47 L40 43 Z") }
    val p243 by lazy { svgPath("M25 62h38v3.5h-38Z") }
    val p244 by lazy { svgPath("M46 22 L63 22 L66 25 L66 42 L49 42 L46 39 Z") }
    val p245 by lazy { svgPath("M54 28h6v6h-6Z") }
    val p246 by lazy { svgPath("M47 37 L51 41 L29 63 L25 59 Z") }
    val p247 by lazy { svgPath("M32 56 L36 60 L32 64 L28 60 Z") }
    val p248 by lazy { svgPath("M39 49 L43 53 L39 57 L35 53 Z") }
    val p249 by lazy { svgPath("M22 55 L22 43 L29 36 L34 36 L40 28 L52 28 L58 35 L64 35 L68 40 L68 55 Z") }
    val p250 by lazy { svgPath("M45 28 L52 28 L58 35 L64 35 L68 40 L68 55 L45 55 Z") }
    val p251 by lazy { svgPath("M39 39 L49 39 L49 46 L44 51 L39 46 Z") }
    val p252 by lazy { svgPath("M42.8 41.5h2.4v4.5h-2.4Z") }
    val p253 by lazy { svgPath("M44 32 L30 23 L33 34 Z") }
    val p254 by lazy { svgPath("M44 32 L58 23 L55 34 Z") }
    val p255 by lazy { svgPath("M26 33h36v7h-36Z") }
    val p256 by lazy { svgPath("M29 42 L59 42 L59 61 L55 65 L33 65 L29 61 Z") }
    val p257 by lazy { svgPath("M44 42 L59 42 L59 61 L55 65 L44 65 Z") }
    val p258 by lazy { svgPath("M40.5 33h7v32h-7Z") }
    val p259 by lazy { svgPath("M44 47 L48 52 L44 57 L40 52 Z") }
    val p260 by lazy { svgPath("M29 23 L59 23 L65 29 L65 59 L59 65 L29 65 L23 59 L23 29 Z") }
    val p261 by lazy { svgPath("M54 30 L37 33 L32 52 L49 49 Z") }
    val p262 by lazy { svgPath("M54 30 L49 49 L32 52 Z") }
    val p263 by lazy { svgPath("M28 56h12v3h-12Z") }
    val p264 by lazy { svgPath("M44 23 L59 23 L65 29 L65 59 L59 65 L44 65 Z") }
    val p265 by lazy { svgPath("M32 27 L56 27 L61 32 L61 56 L56 61 L32 61 L27 56 L27 32 Z") }
    val p266 by lazy { svgPath("M47 30 L34 45 L43 45 L40 58 L54 42 L45 42 Z") }
    val p267 by lazy { svgPath("M31 31h8v8h-8Z") }
    val p268 by lazy { svgPath("M39 31h8v8h-8Z") }
    val p269 by lazy { svgPath("M47 31h8v8h-8Z") }
    val p270 by lazy { svgPath("M31 39h8v8h-8Z") }
    val p271 by lazy { svgPath("M47 39h8v8h-8Z") }
    val p272 by lazy { svgPath("M31 47h16v8h-16Z") }
    val p273 by lazy { svgPath("M47 47h8v8h-8Z") }
}

/** 徽章中心图形（88x88 坐标）。点亮时 c1=paper c2=pri c3=sec dark=plate；没点亮时 blueprint=true、灰色 */
internal fun DrawScope.drawBadgeIcon(key: String, c1: Color, c2: Color, c3: Color, dark: Color, blueprint: Boolean) {
    when (key) {
        "book-open" -> if (blueprint) {
            drawPath(BadgePaths.p0, c1)
            drawPath(BadgePaths.p1, c2)
            drawPath(BadgePaths.p2, c3)
            drawPath(BadgePaths.p3, c1, style = Stroke(2.0f, cap = StrokeCap.Butt, join = StrokeJoin.Miter))
            drawPath(BadgePaths.p4, c1, style = Stroke(2.0f, cap = StrokeCap.Butt, join = StrokeJoin.Miter))
            drawPath(BadgePaths.p5, c1, style = Stroke(2.0f, cap = StrokeCap.Butt, join = StrokeJoin.Miter))
            drawPath(BadgePaths.p6, c1, style = Stroke(2.0f, cap = StrokeCap.Butt, join = StrokeJoin.Miter))
            drawPath(BadgePaths.p7, c3)
        } else {
            drawPath(BadgePaths.p0, c1)
            drawPath(BadgePaths.p1, c2)
            drawPath(BadgePaths.p2, c3)
            drawPath(BadgePaths.p3, dark, style = Stroke(2.0f, cap = StrokeCap.Butt, join = StrokeJoin.Miter))
            drawPath(BadgePaths.p4, dark, style = Stroke(2.0f, cap = StrokeCap.Butt, join = StrokeJoin.Miter))
            drawPath(BadgePaths.p5, dark, style = Stroke(2.0f, cap = StrokeCap.Butt, join = StrokeJoin.Miter))
            drawPath(BadgePaths.p6, dark, style = Stroke(2.0f, cap = StrokeCap.Butt, join = StrokeJoin.Miter))
            drawPath(BadgePaths.p7, c3)
        }
        "clock-1h" -> if (blueprint) {
            drawPath(BadgePaths.p8, c2, style = Stroke(3.0f, cap = StrokeCap.Butt, join = StrokeJoin.Miter))
            drawPath(BadgePaths.p9, c3, alpha = 0.35f)
            drawPath(BadgePaths.p10, c1, style = Stroke(3.2f, cap = StrokeCap.Square, join = StrokeJoin.Miter))
            drawPath(BadgePaths.p11, c2)
        } else {
            drawPath(BadgePaths.p8, c2, style = Stroke(3.0f, cap = StrokeCap.Butt, join = StrokeJoin.Miter))
            drawPath(BadgePaths.p9, c3, alpha = 0.9f)
            drawPath(BadgePaths.p10, c1, style = Stroke(3.2f, cap = StrokeCap.Square, join = StrokeJoin.Miter))
            drawPath(BadgePaths.p11, c2)
        }
        "clock-10h" -> {
            drawPath(BadgePaths.p12, c2, style = Stroke(2.5f, cap = StrokeCap.Butt, join = StrokeJoin.Miter))
            drawPath(BadgePaths.p13, c1)
            drawPath(BadgePaths.p14, c3)
            drawPath(BadgePaths.p15, dark)
            drawPath(BadgePaths.p16, c2)
        }
        "hourglass-50" -> {
            drawPath(BadgePaths.p17, c1)
            drawPath(BadgePaths.p18, c2)
            drawPath(BadgePaths.p19, c2)
            drawPath(BadgePaths.p20, c1)
            drawPath(BadgePaths.p21, c3)
            drawPath(BadgePaths.p22, c1)
        }
        "crown-book" -> {
            drawPath(BadgePaths.p23, c2)
            drawPath(BadgePaths.p24, c1)
            drawPath(BadgePaths.p25, c1)
            drawPath(BadgePaths.p26, c3)
        }
        "leaf-sprout" -> {
            drawPath(BadgePaths.p27, c2)
            drawPath(BadgePaths.p28, c1)
            drawPath(BadgePaths.p29, dark, style = Stroke(2.8f, cap = StrokeCap.Butt, join = StrokeJoin.Miter))
            drawPath(BadgePaths.p30, c3)
        }
        "sun-tarot" -> {
            drawPath(BadgePaths.p31, c2)
            drawPath(BadgePaths.p32, c2)
            drawPath(BadgePaths.p33, c2)
            drawPath(BadgePaths.p34, c1)
            drawPath(BadgePaths.p35, c2)
            drawPath(BadgePaths.p36, c1)
            drawPath(BadgePaths.p37, dark)
        }
        "target-lock" -> {
            drawPath(BadgePaths.p38, c3, style = Stroke(2.8f, cap = StrokeCap.Butt, join = StrokeJoin.Miter))
            drawPath(BadgePaths.p39, c2, style = Stroke(2.8f, cap = StrokeCap.Butt, join = StrokeJoin.Miter))
            drawPath(BadgePaths.p40, c2, style = Stroke(2.8f, cap = StrokeCap.Butt, join = StrokeJoin.Miter))
            drawPath(BadgePaths.p41, c3, style = Stroke(2.8f, cap = StrokeCap.Butt, join = StrokeJoin.Miter))
            drawPath(BadgePaths.p42, c1, style = Stroke(2.2f, cap = StrokeCap.Butt, join = StrokeJoin.Miter))
            drawPath(BadgePaths.p43, c2)
        }
        "night-owl" -> {
            drawPath(BadgePaths.p44, c3, style = Stroke(2.0f, cap = StrokeCap.Butt, join = StrokeJoin.Miter, pathEffect = PathEffect.dashPathEffect(floatArrayOf(5.0f, 3.0f))))
            drawPath(BadgePaths.p45, c2, style = Stroke(1.8f, cap = StrokeCap.Butt, join = StrokeJoin.Miter))
            drawPath(BadgePaths.p46, c2)
            drawPath(BadgePaths.p47, c1)
            drawPath(BadgePaths.p48, dark)
        }
        "early-bird" -> {
            drawPath(BadgePaths.p49, c3)
            drawPath(BadgePaths.p50, c2, style = Stroke(2.5f, cap = StrokeCap.Butt, join = StrokeJoin.Miter))
            drawPath(BadgePaths.p51, c2)
            drawPath(BadgePaths.p52, c1)
            drawPath(BadgePaths.p53, c3)
        }
        "flame-3" -> {
            drawPath(BadgePaths.p54, c2)
            drawPath(BadgePaths.p55, c1)
            drawPath(BadgePaths.p56, dark)
            drawPath(BadgePaths.p57, c3)
            drawPath(BadgePaths.p58, c2)
            drawPath(BadgePaths.p59, c1)
        }
        "flame-7" -> {
            drawPath(BadgePaths.p60, c3)
            drawPath(BadgePaths.p61, c3)
            drawPath(BadgePaths.p62, c2)
            drawPath(BadgePaths.p63, c1)
            drawPath(BadgePaths.p64, dark)
            drawPath(BadgePaths.p65, c2)
        }
        "moon-flame" -> {
            drawPath(BadgePaths.p66, c2)
            drawPath(BadgePaths.p67, c1)
            drawPath(BadgePaths.p68, c3)
            drawPath(BadgePaths.p69, dark)
        }
        "cat-ears" -> {
            drawPath(BadgePaths.p70, c2)
            drawPath(BadgePaths.p71, c1)
            drawPath(BadgePaths.p72, dark)
            drawPath(BadgePaths.p73, dark)
            drawPath(BadgePaths.p74, dark)
            drawPath(BadgePaths.p75, dark)
            drawPath(BadgePaths.p76, c3)
        }
        "bookmark-01" -> {
            drawPath(BadgePaths.p77, c1)
            drawPath(BadgePaths.p78, c2)
            drawPath(BadgePaths.p79, dark)
            drawPath(BadgePaths.p80, dark)
            drawPath(BadgePaths.p81, dark)
        }
        "bookshelf-row" -> {
            drawPath(BadgePaths.p82, c3)
            drawPath(BadgePaths.p83, c2)
            drawPath(BadgePaths.p84, c1)
            drawPath(BadgePaths.p85, c2)
            drawPath(BadgePaths.p86, c1)
        }
        "book-check" -> {
            drawPath(BadgePaths.p87, dark)
            drawPath(BadgePaths.p87, c3, style = Stroke(2.0f, cap = StrokeCap.Butt, join = StrokeJoin.Miter))
            drawPath(BadgePaths.p88, c3)
            drawPath(BadgePaths.p89, c2)
            drawPath(BadgePaths.p90, c1)
        }
        "star-five" -> {
            drawPath(BadgePaths.p91, c2)
            drawPath(BadgePaths.p92, c1)
            drawPath(BadgePaths.p93, dark, style = Stroke(3.0f, cap = StrokeCap.Square, join = StrokeJoin.Miter))
        }
        "crown-ten" -> {
            drawPath(BadgePaths.p94, c2)
            drawPath(BadgePaths.p95, c1)
            drawPath(BadgePaths.p96, c3)
            drawPath(BadgePaths.p97, dark, style = Stroke(2.0f, cap = StrokeCap.Butt, join = StrokeJoin.Miter))
            drawPath(BadgePaths.p98, dark, style = Stroke(2.0f, cap = StrokeCap.Butt, join = StrokeJoin.Miter))
            drawPath(BadgePaths.p99, dark, style = Stroke(2.0f, cap = StrokeCap.Butt, join = StrokeJoin.Miter))
            drawPath(BadgePaths.p100, dark, style = Stroke(2.0f, cap = StrokeCap.Butt, join = StrokeJoin.Miter))
        }
        "paw-single" -> {
            drawPath(BadgePaths.p101, c2)
            drawPath(BadgePaths.p102, c1)
            drawPath(BadgePaths.p103, dark)
            drawPath(BadgePaths.p104, c3)
            drawPath(BadgePaths.p105, c2)
            drawPath(BadgePaths.p106, c1)
            drawPath(BadgePaths.p107, c2)
        }
        "paw-array" -> {
            drawPath(BadgePaths.p108, c3)
            drawPath(BadgePaths.p109, c1)
            drawPath(BadgePaths.p110, c2)
            drawPath(BadgePaths.p111, c2)
            drawPath(BadgePaths.p112, c1)
            drawPath(BadgePaths.p113, c3)
            drawPath(BadgePaths.p114, c1)
        }
        "crown-grand" -> {
            drawPath(BadgePaths.p115, c2)
            drawPath(BadgePaths.p116, c2)
            drawPath(BadgePaths.p117, c1)
            drawPath(BadgePaths.p118, dark)
            drawPath(BadgePaths.p119, c3)
        }
        "sprout-horizon" -> {
            drawPath(BadgePaths.p120, c3, style = Stroke(2.2f, cap = StrokeCap.Butt, join = StrokeJoin.Miter))
            drawPath(BadgePaths.p121, c1)
            drawPath(BadgePaths.p122, c2)
            drawPath(BadgePaths.p123, c1)
            drawPath(BadgePaths.p124, c3)
        }
        "hanger-basic" -> {
            drawPath(BadgePaths.p125, c3, style = Stroke(3.0f, cap = StrokeCap.Square, join = StrokeJoin.Miter))
            drawPath(BadgePaths.p126, c1)
            drawPath(BadgePaths.p127, c2)
            drawPath(BadgePaths.p128, dark)
            drawPath(BadgePaths.p129, c3)
        }
        "hanger-bowtie" -> {
            drawPath(BadgePaths.p130, c2, style = Stroke(3.0f, cap = StrokeCap.Square, join = StrokeJoin.Miter))
            drawPath(BadgePaths.p131, c2)
            drawPath(BadgePaths.p132, c1)
            drawPath(BadgePaths.p133, dark)
            drawPath(BadgePaths.p134, c3)
            drawPath(BadgePaths.p135, c2)
            drawPath(BadgePaths.p136, c1)
        }
        "cyber-cat" -> {
            drawPath(BadgePaths.p137, c2)
            drawPath(BadgePaths.p138, c1)
            drawPath(BadgePaths.p139, dark)
            drawPath(BadgePaths.p140, c3)
            drawPath(BadgePaths.p141, c1)
            drawPath(BadgePaths.p142, c3)
            drawPath(BadgePaths.p143, c2)
        }
        "fox-spirit" -> {
            drawPath(BadgePaths.p144, c2)
            drawPath(BadgePaths.p145, c1)
            drawPath(BadgePaths.p146, c1)
            drawPath(BadgePaths.p147, c3)
            drawPath(BadgePaths.p148, dark)
            drawPath(BadgePaths.p149, dark)
            drawPath(BadgePaths.p150, c3)
        }
        "owl-mecha" -> {
            drawPath(BadgePaths.p151, c3)
            drawPath(BadgePaths.p152, c2)
            drawPath(BadgePaths.p153, dark)
            drawPath(BadgePaths.p154, dark)
            drawPath(BadgePaths.p155, c1)
            drawPath(BadgePaths.p156, c2)
            drawPath(BadgePaths.p157, c1)
        }
        "wizard-hat" -> {
            drawPath(BadgePaths.p158, c2)
            drawPath(BadgePaths.p159, c1)
            drawPath(BadgePaths.p160, c3)
            drawPath(BadgePaths.p161, c1)
            drawPath(BadgePaths.p162, c2)
            drawPath(BadgePaths.p163, c3)
        }
        "glasses-nerd" -> {
            drawPath(BadgePaths.p164, dark)
            drawPath(BadgePaths.p164, c2, style = Stroke(3.0f, cap = StrokeCap.Butt, join = StrokeJoin.Miter))
            drawPath(BadgePaths.p165, dark)
            drawPath(BadgePaths.p165, c1, style = Stroke(3.0f, cap = StrokeCap.Butt, join = StrokeJoin.Miter))
            drawPath(BadgePaths.p166, c3)
            drawPath(BadgePaths.p167, c3)
            drawPath(BadgePaths.p168, c1)
        }
        "bowtie-plaid" -> {
            drawPath(BadgePaths.p169, c2)
            drawPath(BadgePaths.p170, c1)
            drawPath(BadgePaths.p171, c3)
            drawPath(BadgePaths.p172, c3)
            drawPath(BadgePaths.p173, dark)
            drawPath(BadgePaths.p174, c2)
        }
        "royal-cape" -> {
            drawPath(BadgePaths.p175, c1)
            drawPath(BadgePaths.p176, c3)
            drawPath(BadgePaths.p177, c2)
            drawPath(BadgePaths.p178, dark)
            drawPath(BadgePaths.p179, c2)
        }
        "rain-window" -> {
            drawPath(BadgePaths.p180, c2, style = Stroke(3.0f, cap = StrokeCap.Butt, join = StrokeJoin.Miter))
            drawPath(BadgePaths.p181, c2, style = Stroke(2.2f, cap = StrokeCap.Butt, join = StrokeJoin.Miter))
            drawPath(BadgePaths.p182, c2, style = Stroke(2.2f, cap = StrokeCap.Butt, join = StrokeJoin.Miter))
            drawPath(BadgePaths.p183, c3, style = Stroke(2.2f, cap = StrokeCap.Butt, join = StrokeJoin.Miter))
            drawPath(BadgePaths.p184, c1, style = Stroke(2.2f, cap = StrokeCap.Butt, join = StrokeJoin.Miter))
            drawPath(BadgePaths.p185, c1, style = Stroke(2.2f, cap = StrokeCap.Butt, join = StrokeJoin.Miter))
            drawPath(BadgePaths.p186, c3)
        }
        "sun-library" -> if (blueprint) {
            drawPath(BadgePaths.p187, c2, style = Stroke(2.8f, cap = StrokeCap.Butt, join = StrokeJoin.Miter))
            drawPath(BadgePaths.p188, c1)
            drawPath(BadgePaths.p189, c2, alpha = 0.3f)
            drawPath(BadgePaths.p190, c3)
            drawPath(BadgePaths.p191, c3)
        } else {
            drawPath(BadgePaths.p187, c2, style = Stroke(2.8f, cap = StrokeCap.Butt, join = StrokeJoin.Miter))
            drawPath(BadgePaths.p188, c1)
            drawPath(BadgePaths.p189, c2, alpha = 0.85f)
            drawPath(BadgePaths.p190, c3)
            drawPath(BadgePaths.p191, c3)
        }
        "forest-sanctuary" -> {
            drawPath(BadgePaths.p192, c3)
            drawPath(BadgePaths.p193, c2)
            drawPath(BadgePaths.p194, c1)
            drawPath(BadgePaths.p195, c1)
            drawPath(BadgePaths.p196, c2)
            drawPath(BadgePaths.p197, c1)
        }
        "galaxy-train" -> {
            drawPath(BadgePaths.p198, c3, style = Stroke(2.8f, cap = StrokeCap.Butt, join = StrokeJoin.Miter))
            drawPath(BadgePaths.p199, c2, style = Stroke(1.8f, cap = StrokeCap.Butt, join = StrokeJoin.Miter, pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.0f, 3.0f))))
            drawPath(BadgePaths.p200, c2)
            drawPath(BadgePaths.p201, c1)
            withTransform({ rotate(-13.0f, pivot = Offset(28.0f, 44.0f)) }) {
                drawPath(BadgePaths.p202, dark)
            }
            withTransform({ rotate(-13.0f, pivot = Offset(38.0f, 42.0f)) }) {
                drawPath(BadgePaths.p203, dark)
            }
            drawPath(BadgePaths.p204, c1)
            drawPath(BadgePaths.p205, c3)
        }
        "wind-chime" -> {
            drawPath(BadgePaths.p206, c1, style = Stroke(2.5f, cap = StrokeCap.Butt, join = StrokeJoin.Miter))
            drawPath(BadgePaths.p207, c2)
            drawPath(BadgePaths.p208, c1)
            drawPath(BadgePaths.p209, c3, style = Stroke(2.0f, cap = StrokeCap.Butt, join = StrokeJoin.Miter))
            drawPath(BadgePaths.p210, c3)
        }
        "pendulum-clock" -> {
            drawPath(BadgePaths.p211, c2)
            drawPath(BadgePaths.p212, dark)
            drawPath(BadgePaths.p213, c1, style = Stroke(2.2f, cap = StrokeCap.Butt, join = StrokeJoin.Miter))
            drawPath(BadgePaths.p214, c3, style = Stroke(2.8f, cap = StrokeCap.Butt, join = StrokeJoin.Miter))
            drawPath(BadgePaths.p215, c1)
        }
        "warm-lamp" -> {
            drawPath(BadgePaths.p216, c3, style = Stroke(3.0f, cap = StrokeCap.Butt, join = StrokeJoin.Miter))
            drawPath(BadgePaths.p217, c2)
            drawPath(BadgePaths.p218, c1)
            drawPath(BadgePaths.p219, c1)
            drawPath(BadgePaths.p220, c2, style = Stroke(2.5f, cap = StrokeCap.Butt, join = StrokeJoin.Miter))
            drawPath(BadgePaths.p221, c3, style = Stroke(2.2f, cap = StrokeCap.Butt, join = StrokeJoin.Miter))
            drawPath(BadgePaths.p222, c3, style = Stroke(2.2f, cap = StrokeCap.Butt, join = StrokeJoin.Miter))
        }
        "star-mobile" -> {
            drawPath(BadgePaths.p223, c3, style = Stroke(2.5f, cap = StrokeCap.Butt, join = StrokeJoin.Miter))
            drawPath(BadgePaths.p224, c2, style = Stroke(1.8f, cap = StrokeCap.Butt, join = StrokeJoin.Miter))
            drawPath(BadgePaths.p225, c1, style = Stroke(1.8f, cap = StrokeCap.Butt, join = StrokeJoin.Miter))
            drawPath(BadgePaths.p226, c2, style = Stroke(1.8f, cap = StrokeCap.Butt, join = StrokeJoin.Miter))
            drawPath(BadgePaths.p227, c3)
            drawPath(BadgePaths.p228, c1)
            drawPath(BadgePaths.p229, c2)
        }
        "dict-search" -> {
            drawPath(BadgePaths.p230, c3, style = Stroke(3.5f, cap = StrokeCap.Butt, join = StrokeJoin.Miter))
            drawPath(BadgePaths.p231, c2)
            drawPath(BadgePaths.p232, c1)
        }
        "dict-master" -> {
            drawPath(BadgePaths.p233, c3, style = Stroke(2.0f, cap = StrokeCap.Butt, join = StrokeJoin.Miter, pathEffect = PathEffect.dashPathEffect(floatArrayOf(5.0f, 3.0f))))
            drawPath(BadgePaths.p234, c2, style = Stroke(2.0f, cap = StrokeCap.Butt, join = StrokeJoin.Miter, pathEffect = PathEffect.dashPathEffect(floatArrayOf(5.0f, 3.0f))))
            drawPath(BadgePaths.p235, c2)
            drawPath(BadgePaths.p236, dark)
            drawPath(BadgePaths.p237, c3)
            drawPath(BadgePaths.p238, c1)
        }
        "pen-nib" -> {
            drawPath(BadgePaths.p239, c2)
            drawPath(BadgePaths.p240, c1)
            drawPath(BadgePaths.p241, dark, style = Stroke(2.8f, cap = StrokeCap.Butt, join = StrokeJoin.Miter))
            drawPath(BadgePaths.p242, dark)
            drawPath(BadgePaths.p243, c3)
        }
        "cyber-key" -> {
            drawPath(BadgePaths.p244, c2)
            drawPath(BadgePaths.p245, dark)
            drawPath(BadgePaths.p246, c1)
            drawPath(BadgePaths.p247, c3)
            drawPath(BadgePaths.p248, c2)
        }
        "cloud-shield" -> {
            drawPath(BadgePaths.p249, c2)
            drawPath(BadgePaths.p250, c1)
            drawPath(BadgePaths.p251, dark)
            drawPath(BadgePaths.p252, c3)
        }
        "secret-box" -> {
            drawPath(BadgePaths.p253, c3)
            drawPath(BadgePaths.p254, c2)
            drawPath(BadgePaths.p255, c1)
            drawPath(BadgePaths.p256, c3)
            drawPath(BadgePaths.p257, c2)
            drawPath(BadgePaths.p258, c1)
            drawPath(BadgePaths.p259, dark)
        }
        "app-icon-gold" -> {
            drawPath(BadgePaths.p260, dark)
            drawPath(BadgePaths.p260, c2, style = Stroke(3.0f, cap = StrokeCap.Butt, join = StrokeJoin.Miter))
            drawPath(BadgePaths.p261, c2)
            drawPath(BadgePaths.p262, c1)
            drawPath(BadgePaths.p263, c3)
        }
        "app-icon-cyber" -> {
            drawPath(BadgePaths.p260, c3)
            drawPath(BadgePaths.p264, c2)
            drawPath(BadgePaths.p265, dark)
            drawPath(BadgePaths.p266, c1)
        }
        "app-icon-pixel" -> {
            drawPath(BadgePaths.p260, dark)
            drawPath(BadgePaths.p260, c3, style = Stroke(3.0f, cap = StrokeCap.Butt, join = StrokeJoin.Miter))
            drawPath(BadgePaths.p267, c1)
            drawPath(BadgePaths.p268, c2)
            drawPath(BadgePaths.p269, c1)
            drawPath(BadgePaths.p270, c2)
            drawPath(BadgePaths.p271, c3)
            drawPath(BadgePaths.p272, c1)
            drawPath(BadgePaths.p273, c2)
        }
        else -> drawPath(svgPath("M44 28 L58 44 L44 60 L30 44 Z"), c2)
    }
}
