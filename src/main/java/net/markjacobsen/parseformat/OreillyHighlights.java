package net.markjacobsen.parseformat;

import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import com.cffreedom.exceptions.FileSystemException;
import com.cffreedom.utils.Utils;
import com.cffreedom.utils.file.FileUtils;
import com.opencsv.CSVReader;
import com.opencsv.exceptions.CsvValidationException;

public class OreillyHighlights {
	public static void main(String[] args) {
		int lineNum = 0;
		int titleIndex = -1;
		int chapterIndex = -1;
		int highlightIndex = -1;
		int noteIndex = -1;
		String file = Utils.prompt("Please enter the full path to the CSV file");
		String bookTitle = Utils.prompt("Book Title"); // The CSV export sometimes contains all highlights, or highlights from multiple books
		String outputFile = Utils.prompt("Output file ["+bookTitle+"]");
		outputFile = Utils.isNull(outputFile, bookTitle+".html");
		bookTitle = bookTitle.toLowerCase();
		
		String outputDir = FileUtils.getFileDirectory(file);
		String fullOutputFile = FileUtils.buildPath(outputDir, outputFile);
		List<String> outputLines = new ArrayList<>();
		
		try (CSVReader reader = new CSVReader(new FileReader(file))) {
			String[] lineInFile;
			while ((lineInFile = reader.readNext()) != null) {
				lineNum++;
				if (lineNum == 1) {
					for (int x = 0; x < lineInFile.length; x++) {
						if (lineInFile[x].equalsIgnoreCase("Book Title")) {
							titleIndex = x;
						} else if (lineInFile[x].equalsIgnoreCase("Chapter Title")) {
							chapterIndex = x;
						} else if (lineInFile[x].equalsIgnoreCase("Highlight")) {
							highlightIndex = x;
						} else if (lineInFile[x].equalsIgnoreCase("Personal Note")) {
							noteIndex = x;
						}
					}
				} else {
					String title = lineInFile[titleIndex];
					if (title.toLowerCase().contains(bookTitle)) {
						if (outputLines.isEmpty()) {
							outputLines.add("<h1>"+title+"</h1");
						}
						String chapter = lineInFile[chapterIndex];
						String highlight = lineInFile[highlightIndex];
						String note = lineInFile[noteIndex];
						
						outputLines.add("<p><i style='size:small'>"+chapter+"</i><br>"+highlight+"</p>");
						//System.out.println(highlight);
					}
				}
			}
			
			FileUtils.writeLinesToFile(fullOutputFile, outputLines);
			System.out.println("Wrote highlights to: "+fullOutputFile);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (CsvValidationException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (FileSystemException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
}
