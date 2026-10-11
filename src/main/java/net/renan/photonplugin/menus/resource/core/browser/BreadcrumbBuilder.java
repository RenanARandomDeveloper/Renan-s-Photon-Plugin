package net.renan.photonplugin.menus.resource.core.browser;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.regex.Pattern;

public final class BreadcrumbBuilder {

    private BreadcrumbBuilder() {
    }

    public static List<Segment> buildSegments(File currentDir, List<File> roots, int rootSupplierCount,
                                              Function<File, String> folderNameResolver) {
        List<Segment> segments = new ArrayList<>();
        if (currentDir == null) {
            segments.add(new Segment(null, "/"));
            return segments;
        }
        for (File root : roots) {
            String rootAbs = root.getAbsolutePath();
            String currAbs = currentDir.getAbsolutePath();
            boolean isInside = currAbs.equals(rootAbs) || currAbs.startsWith(rootAbs + File.separator);
            if (!isInside) continue;

            if (rootSupplierCount > 1) {
                segments.add(new Segment(null, "/"));
            }
            segments.add(new Segment(root, folderNameResolver.apply(root)));

            String rel = currAbs.substring(rootAbs.length());
            if (!rel.isEmpty()) {
                String[] parts = rel.substring(1).split(Pattern.quote(File.separator));
                File cursor = root;
                for (String part : parts) {
                    cursor = new File(cursor, part);
                    segments.add(new Segment(cursor, folderNameResolver.apply(cursor)));
                }
            }
            return segments;
        }
        segments.add(new Segment(currentDir, folderNameResolver.apply(currentDir)));
        return segments;
    }

    public record Segment(File dir, String label) {
    }
}
