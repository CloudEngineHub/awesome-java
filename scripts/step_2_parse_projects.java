import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Parses the markdown content to extract project entries and saves it in a temporary file.
 *
 * Usage: java step_2_parse_projects.java [input_file] [output_file]
 */
void main(String[] args) throws IOException {
  var inputPath = args.length > 0 ? Path.of(args[0]) : Path.of(Constants.CONTRIBUTE_README_FILE);
  var tmpDir = FileUtils.ensureTmpDirectory();
  var outputPath = args.length > 1 ? Path.of(args[1]) : tmpDir.resolve(Constants.PARSED_PROJECTS_FILE);

  System.out.println("Step 2: Parsing project entries...");
  System.out.printf("Input: %s%n", inputPath.toAbsolutePath());
  System.out.printf("Output: %s%n", outputPath.toAbsolutePath());

  FileUtils.validateInputFile(inputPath);
  var content = FileUtils.readFileContent(inputPath);
  var projectEntries = parseProjectEntries(content);
  writeParsedEntries(outputPath, projectEntries);

  System.out.printf(
    "SUCCESS: Successfully parsed %d project entries!%n",
    projectEntries.size()
  );
  System.out.printf(
    "GitHub repos: %d%n",
    projectEntries.stream().filter(ProjectEntry::isGitHubRepo).count()
  );
}

/**
 * Parses project entries from markdown content.
 */
List<ProjectEntry> parseProjectEntries(String content) {
  var lines = content.split("\n");
  var projectEntries = new ArrayList<ProjectEntry>();
  var inProjectSection = false;
  var currentSection = "";
  var sectionStack = new ArrayList<String>();

  for (int i = 0; i < lines.length; i++) {
    var line = lines[i];
    var isSubsection = line.startsWith(Constants.SUBSECTION);
    var sectionLevel = getSectionLevel(line);

    // Check if we're entering the Projects section
    if (line.startsWith(Constants.PROJECTS_SECTION)) {
      inProjectSection = true;
      continue;
    }
    // Check if we're leaving the Projects section
    if (inProjectSection &&
        line.startsWith(Constants.SECTION) &&
        !isSubsection &&
        sectionLevel == 0 &&
        !line.equals(Constants.PROJECTS_SECTION)
    ) {
      inProjectSection = false;
    }
    if (!inProjectSection) {
      continue;
    }
    // Track section hierarchy - maintain a stack of section levels
    if (sectionLevel >= 3) {
      var sectionName = line.substring(sectionLevel + 1).trim();

      // Adjust stack size to match current level
      while (sectionStack.size() >= sectionLevel - 2) {
        sectionStack.remove(sectionStack.size() - 1);
      }

      // Add current section to stack
      sectionStack.add(sectionName);

      // Build full section path for entries
      if (sectionStack.size() == 1) {
        currentSection = sectionName; // Main section (###)
      } else {
        currentSection = String.join(" > ", sectionStack); // Subsection path
      }
      continue;
    }
    // Skip headers and empty lines
    var isHeader = (line.startsWith("_") && line.endsWith("_"));
    if (isHeader || line.isBlank()) {
      continue;
    }
    // List entries
    if (line.matches(Constants.ENTRY_PATTERN)) {
      var projectEntry = parseProjectEntry(lines, i, currentSection);
      if (projectEntry != null) {
        projectEntries.add(projectEntry);
        i += projectEntry.linesToSkip() - 1;
      }
    }
  }
  return projectEntries;
}

/**
 * Determines the section level based on the number of # symbols.
 * Returns 0 if the line is not a section header.
 */
int getSectionLevel(String line) {
  if (!line.startsWith("#")) {
    return 0;
  }

  int level = 0;
  for (int i = 0; i < line.length() && line.charAt(i) == '#'; i++) {
    level++;
  }

  // Must have at least 3 # symbols and a space after them
  return (level >= 3 && line.length() > level && line.charAt(level) == ' ') ? level : 0;
}

/**
 * Parses a single project entry from the list format.
 */
ProjectEntry parseProjectEntry(String[] lines, int startIndex, String section) {
  var matcher = Constants.PROJECT_PATTERN.matcher(lines[startIndex]);

  if (matcher.find()) {
    var name = matcher.group(1);
    var url = matcher.group(2);
    var description = new StringBuilder(matcher.group(3));
    var linesToSkip = 1;

    // Handle multi-line descriptions
    for (int i = startIndex + 1; i < lines.length; i++) {
      var nextLine = lines[i];

      if (nextLine.isBlank() ||
          nextLine.startsWith(Constants.SECTION) ||
          nextLine.startsWith(Constants.SUBSECTION) ||
          getSectionLevel(nextLine) > 0 ||
          nextLine.matches(Constants.ENTRY_PATTERN)
      ) {
        break;
      }

      if (nextLine.matches(Constants.INDENTED_LINE_PATTERN)) {
        description.append(" ").append(nextLine.trim());
        linesToSkip++;
      } else if (!nextLine.trim().isEmpty()) {
        description.append(" ").append(nextLine.trim());
        linesToSkip++;
      }
    }
    return new ProjectEntry(
      name,
      url,
      description.toString(),
      linesToSkip,
      section
    );
  }
  return null;
}

/**
 * Writes parsed entries to a temporary file in a simple format.
 */
void writeParsedEntries(Path outputPath, List<ProjectEntry> entries) throws IOException {
  var content = entries.stream()
      .map(e -> """
        %s%s
        %s%s
        %s%s
        %s%d
        %s%s
        %s
        """.formatted(
          Constants.NAME_PREFIX, e.name(),
          Constants.URL_PREFIX, e.url(),
          Constants.DESC_PREFIX, e.description(),
          Constants.SKIP_PREFIX, e.linesToSkip(),
          Constants.SECTION_PREFIX, e.section(),
          Constants.SECTION_SEPARATOR
        ))
      .collect(Collectors.joining("\n"));

  FileUtils.writeOutputFile(outputPath, content);
}
