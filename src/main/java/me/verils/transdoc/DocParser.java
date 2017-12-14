package me.verils.transdoc;

import me.verils.transdoc.model.DocContent;
import me.verils.transdoc.model.DocParagraph;
import me.verils.transdoc.model.DocTable;
import org.apache.poi.hwpf.HWPFDocument;
import org.apache.poi.hwpf.extractor.WordExtractor;
import org.apache.poi.hwpf.model.PicturesTable;
import org.apache.poi.hwpf.model.StyleDescription;
import org.apache.poi.hwpf.model.StyleSheet;
import org.apache.poi.hwpf.usermodel.*;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

public class DocParser {

	/** doc文档对象 */
	private HWPFDocument doc;
	/** 文档整体范围 */
	private Range docRange;
	/** 文档内容 */
	private DocContent docContent;
	/** 文档的图片对象 */
	private List<DocPicture> pictures;

	/**
	 * 私有构造函数
	 * 
	 * @param file
	 *            文档所在的文件
	 * @throws Exception
	 *             解析时如有问题则返回异常
	 */
	public DocParser(File file) throws Exception {
		doc = new HWPFDocument(new FileInputStream(file));
		docRange = doc.getRange();
		docContent = new DocContent();
		parsePictures();
		parseTables();
		parseParagraphs(docRange);
	}

	/**
	 * 将doc文档中的图片导出到指定的目录,并按照给定的格式命名.如果目录不存在,则会创建目录
	 * 
	 * @param picDir
	 *            存放导出图片的目录
	 * @param picNamePattern
	 *            图片命名格式
	 * @throws IOException
	 */
	public void extractPicturesToFiles(File picDir, String picNamePattern) throws IOException {
		docContent.getPicturePaths().clear();
		if (!pictures.isEmpty()) {
			picDir.mkdirs();
			String picDirName = picDir.getName();
			for (int i = 0, size = pictures.size(); i < size; i++) {
				Picture picture = pictures.get(i).picture;
				String filename = picNamePattern.replace("%d", (i < 10 ? "0" : "") + i) + "."
						+ picture.suggestFileExtension();
				File picFile = new File(picDir, filename);
				docContent.getPicturePaths().add("./" + picDirName + "/" + picFile.getName());
				FileOutputStream fos = new FileOutputStream(picFile);
				picture.writeImageContent(fos);
				fos.flush();
				fos.close();
			}
		}
	}

	public DocContent getDocContent() {
		return docContent;
	}

	/**
	 * 解析doc文档,获取所有表格的内容
	 */
	private void parseTables() {
		LinkedList<DocTable> tables = new LinkedList<DocTable>();
		TableIterator tableIterator = new TableIterator(docRange);
		// 遍历表格
		while (tableIterator.hasNext()) {
			Table table = tableIterator.next();
			int numRows = table.numRows();
			TableRow tr = table.getRow(0);
			int numCells = tr.numCells();
			DocTable docTable = null;
			if (numRows == 1 && numCells == 1) {
				// 代码块
				docTable = new DocTable(numRows, numCells);
				TableCell cell = tr.getCell(0);
				docTable.setCell(0, 0, this.getCellContent(cell, true));
			} else {
				// 正常表格
				int maxNumCells = numCells;
				for (int i = 1; i < numRows; i++) {
					tr = table.getRow(i);
					numCells = tr.numCells();
					maxNumCells = maxNumCells >= numCells ? maxNumCells : numCells;
				}
				docTable = new DocTable(numRows, maxNumCells);
				for (int i = 0; i < numRows; i++) {
					tr = table.getRow(i);
					numCells = tr.numCells();
					for (int j = 0; j < maxNumCells; j++) {
						DocContent cellContent = new DocContent();
						if (j < numCells) {
							TableCell cell = tr.getCell(j);
							cellContent = this.getCellContent(cell, false);
						}
						docTable.setCell(i, j, cellContent);
					}
				}
			}
			tables.add(docTable);
		}
		docContent.setTables(tables);
	}

	/**
	 * 提取单元格的文本内容
	 * 
	 * @param cell
	 *            单元格
	 * @param isCodeBlock
	 *            是否是代码块
	 * @return 提取出来的文本内容
	 */
	private DocContent getCellContent(TableCell cell, boolean isCodeBlock) {
		int numParagraphs = cell.numParagraphs();
		List<DocParagraph> paragraphs = new ArrayList<DocParagraph>();
		for (int k = 0; k < numParagraphs; k++) {
			String text = WordExtractor.stripFields(cell.getParagraph(k).text());
			if (!isCodeBlock) {
				text = text.trim();
			}
			text = rtrim(text);
			if (!"".equals(text)) {
				paragraphs.add(new DocParagraph(text));
			}
		}
		return new DocContent(paragraphs);
	}

	/**
	 * 解析doc文档,获取所有图片内容
	 */
	private void parsePictures() {
		List<DocPicture> docPictures = new ArrayList<DocPicture>();
		PicturesTable picturesTable = doc.getPicturesTable();
		int numCharacterRuns = docRange.numCharacterRuns();
		for (int i = 0; i < numCharacterRuns; i++) {
			CharacterRun characterRun = docRange.getCharacterRun(i);
			if (picturesTable.hasPicture(characterRun)) {
				Picture picture = picturesTable.extractPicture(characterRun, false);
				docPictures.add(new DocPicture(characterRun, picture));
			}
		}
		this.pictures = docPictures;
		docContent.setPicturePaths(new LinkedList<String>());
	}

	/**
	 * 解析doc文档,获取所有段落,包括表格和图片,以占位字符串的形式表示
	 * 
	 * @throws Exception
	 *             解析时出现的异常
	 */
	private void parseParagraphs(Range range) throws Exception {
		LinkedList<DocParagraph> paragraphs = new LinkedList<DocParagraph>();
		int numParagraphs = range.numParagraphs();
		int picIndex = 0, listNum = 0, listIndex = 0;
		boolean isInList = false;
		for (int i = 0; i < numParagraphs; i++) {
			Paragraph paragraph = range.getParagraph(i);

			int numStyles = doc.getStyleSheet().numStyles();
			int styleIndex = paragraph.getStyleIndex();
			String styleName = "";
			if (numStyles > styleIndex) {
				StyleSheet style_sheet = doc.getStyleSheet();
				StyleDescription style = style_sheet.getStyleDescription(styleIndex);
				styleName = style.getName();
				if (styleName != null && styleName.contains("标题") && !styleName.startsWith("目录标题")) {
					genTitle(WordExtractor.stripFields(paragraph.text()).trim(),paragraphs,styleName);
					continue;
				}
			}

			if (hasPicture(paragraph, picIndex)) {
				// 图片
				picIndex++;
				DocParagraph docParagraph = new DocParagraph("{picture}");
				docParagraph.setInList(isInList);
				paragraphs.add(docParagraph);
			} else if (paragraph.isInTable()) {
				// 表格或代码块
				if (paragraphs.isEmpty() || !"{table}".equals(paragraphs.getLast().getContent())) {
					DocParagraph docParagraph = new DocParagraph("{table}");
					docParagraph.setInList(isInList);
					paragraphs.add(docParagraph);
				}
			} else if (paragraph.isInList()) {
				// 列表
				StringBuilder content = new StringBuilder();
				// 标题段落处理(由doc段落大纲级别定义)
				if (paragraph.getIlfo() != listNum) {
					listNum = paragraph.getIlfo();
					listIndex = 0;
				}
				content.append("{l").append(++listIndex).append("}")
						.append(WordExtractor.stripFields(paragraph.text()).trim());
				DocParagraph docParagraph = new DocParagraph(content.toString());
				docParagraph.setInList(false);
				paragraphs.add(docParagraph);
				isInList = true;
				// System.out.println("listNum: " + listNum + ", listIndex: " +
				// listIndex + ", listLvl: " + listLvl);
			} else {
				// 文字
				String text = paragraph.text();
				if (null == text || "".equals(text.trim())) {
					continue;
				}
				DocParagraph docParagraph = new DocParagraph(null);
				StringBuilder content = new StringBuilder();
				int lvl = paragraph.getLvl();
				if (lvl >= 0 && lvl < 6) {
					// 标题段落处理(由doc段落大纲级别定义)
					content.append("{h").append(++lvl).append("}").append(WordExtractor.stripFields(paragraph.text()).trim());
					isInList = false;
				} else  {
					int numCharacterRuns = paragraph.numCharacterRuns();
					for (int j = 0; j < numCharacterRuns; j++) {
						// 检测文本样式
						CharacterRun characterRun = paragraph.getCharacterRun(j);
						String prefix = "", suffix = "";
						if (!"".equals(characterRun.text().trim())) {
							if (characterRun.isBold()) {
								prefix = suffix = "**";
							} else if (characterRun.isItalic()) {
								prefix = suffix = "*";
							}
						}
						if (content.length() > 0 && content.charAt(content.length() - 1) == '*') {
							content.append(" ");
						}
						content.append(prefix).append(characterRun.text()).append(suffix);
					}
				}
				String contentStr = WordExtractor.stripFields(content.toString()).trim();
				docParagraph.setInList(isInList);
				docParagraph.setContent(contentStr);
				paragraphs.add(docParagraph);
			}
		}
		docContent.setParagraphs(paragraphs);
	}


	private Integer title1 = 1 ;
	private Integer title2 = 1;
	private Integer title3 = 1 ;

	private void genTitle(String content ,LinkedList<DocParagraph> paragraphs, String styleName) {
		 if (styleName.contains("1")) {
			 DocParagraph title1Content = new DocParagraph("{h1}"+  title1 + " " + content ) ;
			 paragraphs.add(title1Content);
			 title1++;
			 title2 = 1;
			 title3 = 1;
			 return;
		 }

		if (styleName.contains("2")) {
			DocParagraph title1Content = new DocParagraph("{h2}" + (title1-1) + "." + title2 + " " + content) ;
			paragraphs.add(title1Content);
			title2++;
			title3 = 1;
			return;
		}
		if (styleName.contains("3")) {
			DocParagraph title1Content = new DocParagraph("{h3}" + (title1 - 1) + "." + (title2-1) + "." + title3 + " " + content) ;
			paragraphs.add(title1Content);
			title3++;
			return;
		}

	}

	/**
	 * 判断段落中是否包含图片
	 * 
	 * @param paragraph
	 * @param picIndex
	 * @param picIndex
	 *            已解析并识别的图片序号
	 * @return
	 */
	private boolean hasPicture(Paragraph paragraph, int picIndex) {
		if (picIndex >= pictures.size()) {
			return false;
		}
		DocPicture docPicture = pictures.get(picIndex);
		CharacterRun characterRun = docPicture.characterRun;
		return characterRun.getStartOffset() >= paragraph.getStartOffset()
				&& characterRun.getEndOffset() <= paragraph.getEndOffset();
	}

	private String rtrim(String str) {
		int len = str.length();
		char[] val = str.toCharArray();
		while (len > 0 && val[len - 1] <= ' ') {
			len--;
		}
		return str.substring(0, len);
	}
}

/**
 * 内部类,装载doc文件解析出的picture对象和对应的characterRun
 */
class DocPicture {
	CharacterRun characterRun;
	Picture picture;

	DocPicture(CharacterRun characterRun, Picture picture) {
		super();
		this.characterRun = characterRun;
		this.picture = picture;
	}
}