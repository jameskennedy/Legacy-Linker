Legacy Linker is a tool for showing stats on Java code that "covers" legacy Cobol programs.  It assumes that there is a
local Git repository that syncs with Subversion.  It uses a JavaGit library to continuously pull the .java file history
into its database and "link" the java to specific Cobol programs via '@legacy' javadoc annotations.

The UI is is essentially a Cobol program centric view that shows a list of covering Java files and the integrated commit
 history of the selected files.  It also shows pie charts of calculated "authorship" of the Cobol program and it's 
covering .java files. The screenshot says it all.

You can read more about this project here:
http://james.hunterkennedy.ca/2011/12/24/review-of-the-play-framework/

To use Legacy Linker you will need to:

* Have a local git repository with java code that contains class/method javadocs with @legacy <programName> references.
* Have a local directory containing Cobol source files whose names match the @legacy references.

In conf/application.conf, make sure you are referencing those directories properly:

application.git_repository=${pathToYourGitRepo}
application.legacy_src_dir=${pathToYourCobolDir}

Note that application.git_repository is used only once to bootstrap the database. It is subsequently ignored as the original
value is read from the Repository table in the database.

By default, the app will automatically create an H2 database and no further configuration is necessary.
Configure the rest of conf/application.conf as you would any Play Framework application.