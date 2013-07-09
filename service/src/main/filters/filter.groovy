import groovy.io.FileType
import groovy.text.SimpleTemplateEngine
import java.util.regex.Matcher
import java.util.regex.Pattern
import org.yaml.snakeyaml.Yaml


class NoSuchResourceException extends Exception {
    NoSuchResourceException(String reason) {
        super(reason)
    }
}

class BackslashException extends Exception {
    BackslashException(String reason) {
        super(reason)
    }
}

class GroovyMavenFilter {
    def data
    final def PATTERN = Pattern.compile(/.*(\$\{(.*)\}).*/)

    GroovyMavenFilter(String configYaml, def variables) {
        Yaml yaml = new Yaml();
        File f = new File(configYaml)

        //The Template engine doesn't quite parse out \'s correctly, so we need to barf if there are any backslashes in there
        if (f.getText().contains("\\")) {
            throw new BackslashException("YAML FILE CONTAINS A BACKSLASH, THIS IS A BIG NO NO!")
        }

        def engine = new SimpleTemplateEngine()
        //This gives it access to the simple maven properties that exist
        // project.basedir, etc.
        data = yaml.load(engine.createTemplate(f.getText()).make(variables).toString().replaceAll(Matcher.quoteReplacement("\\"), "/"))
    }

    /**
     * This is the first level of items in the YAML file
     * @return
     */
    def environments() {
        unCommon(data)
    }

    def dataCenters(def environment) {
        unCommon(data[environment])
    }

    /**
     * removes common from the keys list and returns everything else
     * @param hash
     * @return a list of keys that doesn't include common
     */
    def unCommon(def hash) {
        hash.keySet().collect {
            if (it != "common")
                it
        } - null
    }

    /**
     * This actually filters the file
     * Follows the maven resource convention of ${thingy.thinger}* @param sourceFile
     * @param targetEnv
     * @param destinationFile
     * @return
     */
    def filterFile(def sourceFile, def targetEnv, def destinationFile) {
        File source = new File(sourceFile)
        File destination = new File(destinationFile)
        //Make sure we've got a directory to barf the files into
        destination.getParentFile().mkdirs()
        destination.withPrintWriter { writer ->
            source.eachLine { line ->
                def matcher = PATTERN.matcher(line)
                if (matcher.matches()) {
                    //println "matched ${matcher.groupCount()} groups"
                    //println("matched group: ${matcher.group(1)}")
                    //println "group 2: ${matcher.group(2)}"
                    def g2 = matcher.group(2)
                    def g1 = matcher.group(1)

                    String replacement = null
                    if (data[targetEnv]?.containsKey(g2)) {
                        replacement = data[targetEnv][g2]
                    } else if (data["common"]?.containsKey(g2)) {
                        replacement = data["common"][g2]
                    } else {
                        throw new NoSuchResourceException("No such resource defined: ${g2}")
                    }

                    if (replacement == null) {
                        replacement = ""
                    }
                    writer.println(line.replace(g1, replacement))
                } else {
                    writer.println(line)
                }
            }
        }
    }
}

def fs = File.separator

def sourcePath = "${project.basedir}${fs}src${fs}main${fs}config"
def filterConfig = "${project.basedir}${fs}target${fs}filter.yaml"

//Pass in the simple variables that make it all work
GroovyMavenFilter gmf = new GroovyMavenFilter(filterConfig, this.binding.variables)

File configs = new File(sourcePath)
gmf.environments().each { env ->
    def targetPathBase = "${project.basedir}${fs}target${fs}properties${fs}${env}"
    configs.eachFileRecurse(FileType.FILES) {file ->
        //target/properties/test
        def fileNameWithPath = file.path.replace(sourcePath,targetPathBase)
        println "Filtering file: ${file.getAbsolutePath()} in environment ${env}"
        gmf.filterFile(file.getAbsolutePath(), env, fileNameWithPath)
    }
}

