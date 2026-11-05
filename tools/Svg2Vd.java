import com.android.ide.common.vectordrawable.Svg2Vector;

import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * SVG → Android VectorDrawable，用 Android Studio 自带的 Svg2Vector（tools/import_shop_assets.py 调用）。
 * 参数：若干对 "输入 SVG 路径" "输出 XML 路径"。转不了的元素（比如 text）只打警告。
 */
public class Svg2Vd {
    public static void main(String[] args) throws Exception {
        for (int i = 0; i + 1 < args.length; i += 2) {
            Path src = Paths.get(args[i]);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            String err = Svg2Vector.parseSvgToXml(src, out);
            Files.write(Paths.get(args[i + 1]), out.toByteArray());
            if (err != null && !err.isEmpty()) System.out.println("WARN " + src.getFileName() + ": " + err.replace('\n', ' '));
        }
    }
}
