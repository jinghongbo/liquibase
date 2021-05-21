package liquibase.configuration.core;

import liquibase.Scope;
import liquibase.command.CommandDefinition;
import liquibase.command.CommandFactory;
import liquibase.command.CommandScope;
import liquibase.configuration.AbstractMapConfigurationValueProvider;
import liquibase.configuration.LiquibaseConfiguration;
import liquibase.exception.UnexpectedLiquibaseException;
import liquibase.servicelocator.LiquibaseService;
import liquibase.util.StringUtil;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.Map;
import java.util.Properties;
import java.util.SortedSet;
import java.util.TreeSet;

@LiquibaseService(skip = true)
public class DefaultsFileValueProvider extends AbstractMapConfigurationValueProvider {

    private final Properties properties;
    private final String sourceDescription;

    public DefaultsFileValueProvider(File path) {
        this.sourceDescription = "File " + path.getAbsolutePath();

        try (InputStream stream = new FileInputStream(path)) {
            this.properties = new Properties();
            this.properties.load(stream);
            trimAllProperties();
        } catch (Exception e) {
            throw new UnexpectedLiquibaseException(e);
        }
    }

    @Override
    public void validate(CommandScope commandScope) throws IllegalArgumentException {
        SortedSet<String> invalidKeys = new TreeSet<>();
        for (Map.Entry<Object, Object> entry : this.properties.entrySet()) {
            String key = (String) entry.getKey();
            key = StringUtil.toCamelCase(key);
            String originalKey = key;

            if (key.equalsIgnoreCase("strict")) {
                continue;
            }

            if (key.startsWith("property.")) {
                continue;
            }

            final String genericCommandPrefix = "liquibase.command.";
            final String targettedCommandPrefix = "liquibase.command." + StringUtil.join(commandScope.getCommand().getName(), ".") + ".";
            if (!key.contains(".")) {
                if (commandScope.getCommand().getArgument(key) == null) {
                    if (!key.contains(".")) {
                        key = "liquibase." + key;
                    }

                    if (Scope.getCurrentScope().getSingleton(LiquibaseConfiguration.class).getRegisteredDefinition(key) == null) {
                        invalidKeys.add(" - '" + originalKey + "'");
                    }
                }
            } else if (key.startsWith(targettedCommandPrefix)) {
                String keyAsArg = key.replace(targettedCommandPrefix, "");
                if (commandScope.getCommand().getArgument(keyAsArg) == null) {
                    invalidKeys.add(" - '" + originalKey + "'");
                }
            } else if (key.startsWith(genericCommandPrefix)) {
                String keyAsArg = key.replace(genericCommandPrefix, "");

                boolean foundMatch = false;
                for (CommandDefinition definition : Scope.getCurrentScope().getSingleton(CommandFactory.class).getCommands(true)) {
                    if (definition.getArgument(keyAsArg) != null) {
                        foundMatch = true;
                        break;
                    }
                }
                if (!foundMatch) {
                    invalidKeys.add(" - '" + originalKey + "'");
                }
            } else {
                if (Scope.getCurrentScope().getSingleton(LiquibaseConfiguration.class).getRegisteredDefinition(key) == null) {
                    invalidKeys.add(" - '" + originalKey + "'");
                }
            }
        }

        if (invalidKeys.size() > 0) {
            if (this.properties.getProperty("strict", "false").equalsIgnoreCase("true")) {
                throw new IllegalArgumentException("Strict check failed due to undefined key(s) for '" + StringUtil.join(commandScope.getCommand().getName(), " ")
                        + "' command in " + StringUtil.lowerCaseFirst(sourceDescription) + "':\n"
                        + StringUtil.join(invalidKeys, "\n")
                        + "\nTo define keys that could apply to any command, prefix it with 'liquibase.command.'\nTo disable strict checking, remove 'strict' from the file.");
            } else {
                Scope.getCurrentScope().getLog(getClass()).warning("Potentially ignored key(s) in " + StringUtil.lowerCaseFirst(sourceDescription) + "\n" + StringUtil.join(invalidKeys, "\n"));
            }
        }
    }

    //
    // Remove trailing spaces on the property file values
    //
    private void trimAllProperties() {
        properties.forEach((key, value) -> {
            if (value == null) {
                return;
            }
            if (!(value instanceof String)) {
                return;
            }
            properties.put(key, StringUtil.trimToEmpty((String) value));
        });
    }

    protected DefaultsFileValueProvider(Properties properties) {
        this.properties = properties;
        sourceDescription = "Passed default properties";
    }

    @Override
    public int getPrecedence() {
        return 50;
    }

    @Override
    protected Map<?, ?> getMap() {
        return properties;
    }

    @Override
    protected boolean keyMatches(String wantedKey, String storedKey) {
        if (super.keyMatches(wantedKey, storedKey)) {
            return true;
        }

        if (wantedKey.replaceFirst("^liquibase\\.", "").equalsIgnoreCase(StringUtil.toCamelCase(storedKey))
                || wantedKey.replaceFirst("^liquibase\\.command\\.", "").equalsIgnoreCase(StringUtil.toCamelCase(storedKey))) {
            //Stored the argument name without a prefix
            return true;
        }

        return false;
    }

    @Override
    protected String getSourceDescription() {
        return sourceDescription;
    }
}
