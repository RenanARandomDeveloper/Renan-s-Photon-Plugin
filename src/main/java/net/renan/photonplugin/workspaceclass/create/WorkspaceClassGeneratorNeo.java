package net.renan.photonplugin.workspaceclass.create;

import net.mcreator.ui.MCreator;

public class WorkspaceClassGeneratorNeo extends WorkspaceClassGenerator {

    private static final String TEMPLATE_ROOT_1_21_1 = "/neoforge-1.21.1/templates/";
    private static final String TEMPLATE_ROOT_26_1_2 = "/neoforge-26.1.2/templates/";
    private static final String TEMPLATE_ROOT_26_2 = "/neoforge-26.2/templates/";

    public WorkspaceClassGeneratorNeo(MCreator mcreator) {
        super(mcreator, resolveTemplateRoot(mcreator));
    }

    private static String resolveTemplateRoot(MCreator mcreator) {
        if (mcreator == null) {
            return TEMPLATE_ROOT_1_21_1;
        }

        var generator = mcreator.getGenerator();
        String generatorName = generator == null ? null : generator.getGeneratorName();

        if (generatorName == null) {
            return TEMPLATE_ROOT_1_21_1;
        }

        if (containsVersionToken(generatorName, "26.1.2")) return TEMPLATE_ROOT_26_1_2;
        if (containsVersionToken(generatorName, "26.2")) return TEMPLATE_ROOT_26_2;

        return TEMPLATE_ROOT_1_21_1;
    }

    private static boolean containsVersionToken(String source, String token) {
        int index = source.indexOf(token);
        while (index != -1) {
            boolean precededByDigit = index > 0 && Character.isDigit(source.charAt(index - 1));
            int afterIndex = index + token.length();
            boolean followedByDigit = afterIndex < source.length() && Character.isDigit(source.charAt(afterIndex));

            if (!precededByDigit && !followedByDigit) {
                return true;
            }

            index = source.indexOf(token, index + 1);
        }
        return false;
    }

    public static GenerationResult generate(MCreator mcreator) {
        return new WorkspaceClassGeneratorNeo(mcreator).generate();
    }
}