package me.verils.transdoc;

import me.verils.transdoc.model.DocContent;
import me.verils.transdoc.model.DocParagraph;
import me.verils.transdoc.model.DocTable;

import java.util.List;
import java.util.Queue;

public class MarkdownConverter {

	private DocContent docContent;

	public MarkdownConverter(DocContent docContent) {
		super();
		this.docContent = docContent;
	}

	public String toMdString() {
		return docContent == null ? "" : toMdString(docContent);
	}

	private String toMdString(DocContent docContent) {
		List<DocParagraph> paragraphs = docContent.listParagraphs();
		Queue<DocTable> tables = docContent.listTables();
		Queue<String> pictures = docContent.listPictures();

		// 将文档信息进行简单组装,获取md格式文本
		StringBuilder mdContent = new StringBuilder();
		for (DocParagraph paragraph : paragraphs) {
			String content = paragraph.getContent();
			boolean isInList = paragraph.isInList();
			if (isInList) {
				mdContent.append("\t");
			}
			if ("{picture}".equals(content)) {
				// 图片
				content = "![image](" + pictures.poll() + ")";
			} else if ("{table}".equals(content)) {
				// 表格
				content = toTableMDText(tables.poll());
				content = isInList ? content.replaceAll("\n", "\n\t") : content;
			} else if (content.startsWith("{h")) {
				// 标题
				int lvl = content.charAt(2) - 48;
				if (lvl != 1) {
					// 题目前不添加空行
					mdContent.append("\n");
				}
				for (int i = 0; i <= lvl; i++) {
					mdContent.append("#");
				}
				mdContent.append(" ");
				content = content.substring(4);
			} else if (content.startsWith("{l")) {
				// 列表
				//{l2}用户在客户端内点击消息或者扫码打开网页。
				String index = content.substring(2,content.indexOf("}"));
				mdContent.append(index).append("、");
				content = content.substring(content.indexOf("}") + 1) + "\n";
			}else if(content.startsWith("<code>")){
				content = "```\n" + content.substring(6) + "\n```\n" ;
			}
			mdContent.append(content).append("\n\n");
		}
		String md = mdContent.toString();
		md = md.replaceAll("\n\n\n*", "\n\n");
		return md;

	}

	/**
	 * 将表格数据对象转换为有效的md格式文本
	 * 
	 * @param table
	 *            表格对象
	 * @return md格式的表格内容
	 */
	private String toTableMDText(DocTable table) {
		StringBuilder tableContent = new StringBuilder();
		if (table == null) {
		} else if (table.isBlock()) {
			tableContent.append("```\n").append(this.toMdString(table.getCell(0, 0)).trim()).append("\n```");
		} else {
			int rownum = table.getRownum();
			int colnum = table.getColnum();
			// tableContent.append("\n");
			for (int i = 0; i < rownum; i++) {
				tableContent.append("|");
				for (int j = 0; j < colnum; j++) {
					String mdString = this.toMdString(table.getCell(i, j));
					mdString = mdString.trim().replaceAll("\n", "<br>");
					tableContent.append(mdString).append("|");
				}
				if (i == 0) {
					tableContent.append("\n|");
					for (int j = 0; j < colnum; j++) {
						tableContent.append("----|");
					}
				}
				tableContent.append("\n");
			}
		}
		return tableContent.toString();
	}
}
