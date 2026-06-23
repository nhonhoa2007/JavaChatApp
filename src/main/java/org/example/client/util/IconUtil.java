package org.example.client.util;

import javafx.scene.shape.SVGPath;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class IconUtil {
    private static final Pattern PATH_DATA_PATTERN = Pattern.compile("<path\\b[^>]*\\bd=\"([^\"]+)\"");

    private IconUtil() {
    }

    public static SVGPath createIcon(String resourcePath, String styleClass, double scale) {
        SVGPath icon = new SVGPath();
        icon.setContent(loadPathData(resourcePath));
        icon.setScaleX(scale);
        icon.setScaleY(scale);
        icon.getStyleClass().add(styleClass);
        return icon;
    }

    private static String loadPathData(String resourcePath) {
        try (InputStream stream = IconUtil.class.getResourceAsStream(resourcePath)) {
            if (stream == null) {
                throw new IllegalArgumentException("Icon resource not found: " + resourcePath);
            }
            String svg = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
            Matcher matcher = PATH_DATA_PATTERN.matcher(svg);
            if (!matcher.find()) {
                throw new IllegalArgumentException("Icon SVG has no path data: " + resourcePath);
            }
            return matcher.group(1);
        } catch (IOException e) {
            throw new IllegalStateException("Cannot load icon resource: " + resourcePath, e);
        }
    }
}
