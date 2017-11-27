package me.verils.transdoc;

import me.verils.transdoc.model.DocContent;

import java.io.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

public class Transdoc {

	private static final String DEFAULT_PROPERTIES_FILE_NAME = "config.properties";

	/** 需要转换文档的源目录 */
	private String srcDirPath;
	/** 转换文档结果存放目录 */
	private String dstDirPath;
	/** 从文档中提取的图片存放目录名称 */
	private String picDirName;
	/** 从文档中提取的图片命名模式 */
	private String picFilenamePattern;
	private List<File> docFiles;

	private FileFilter docFileFilter = new FileFilter() {
		@Override
		public boolean accept(File file) {
			String filename = file.getName();
			return file.isFile() && filename.endsWith(".doc") && !filename.startsWith("~");
		}
	};

	/**
	 * 获取参数传入的文档文件路径进行处理
	 * 
	 * @throws IOException
	 */
	public Transdoc() throws IOException {
		System.out.println("========== Transdoc ==========");
		docFiles = new ArrayList<File>();
		readPropFile();
	}

	/**
	 * 从给定的文件路径列表添加待转换的文件
	 * 
	 * @param paths
	 *            待转换的文件路径数组
	 */
	public void prepareFiles(String[] paths) {
		for (String inputPath : paths) {
			File file = new File(inputPath);
			String filename = file.getName();
			if (file.exists() && filename.endsWith(".doc") && !filename.startsWith("~")) {
				if (!docFiles.contains(file)) {
					if (filename.equals("md.doc")) {
						docFiles.add(file);
					}
				}
			} else {
				System.err.println(filename + " - 不是有效的doc文件");
			}
		}
	}

	/**
	 * 读取配置文件,扫描已设置的源文件夹,将满足条件的文件进行转换
	 */
	public void prepareFromProperties() throws IOException {
		// 扫描docs目录,处理文档
		File srcDir = new File(srcDirPath);
		String canonicalPath = srcDir.getCanonicalPath();
		if (!srcDir.exists()) {
			System.err.println(canonicalPath + "目录不存在,未执行文件扫描");
		} else if (!srcDir.isDirectory()) {
			System.err.println(canonicalPath + "不是有效的目录,未执行文件扫描");
		} else {
			File[] listFiles = srcDir.listFiles(docFileFilter);
			if (listFiles != null && listFiles.length > 0) {
				for (File file : listFiles) {
					if (!docFiles.contains(file)) {
						if (file.getName().equals("md.doc")) {
							docFiles.add(file);
						}
					}
				}
			} else {
				System.err.println(canonicalPath + "目录中没有可转换的文件");
			}
		}
	}

	/**
	 * 将文档转换为MD格式文件
	 * 
	 * @throws IOException
	 */
	public void convertToMD() throws IOException {
		for (final File file : docFiles) {
			// 利用多线程处理文档文件
			new Thread() {
				@Override
				public void run() {
					try {
						doConvertToMD(file);
						System.out.println(file.getName() + " - 转换成功!!");
					} catch (Exception e) {
						System.err.println(file.getName() + " - 转换失败!!");
					}
				}
			}.start();
		}
	}

	/**
	 * 读取配置文件信息
	 * 
	 * @throws IOException
	 *             读取配置文件失败,则抛出IO异常
	 */
	private void readPropFile() throws IOException {
		File propFile = new File("./bin", DEFAULT_PROPERTIES_FILE_NAME);
		InputStream input = null;
		if (propFile.exists()) {
			input = new FileInputStream(propFile);
		} else {
			input = Transdoc.class.getResourceAsStream("/" + DEFAULT_PROPERTIES_FILE_NAME);
		}
		Properties properties = new Properties();
		properties.load(input);
		srcDirPath = properties.getProperty("srcDir");
		dstDirPath = properties.getProperty("dstDir");
		picDirName = properties.getProperty("pictureDirName");
		picFilenamePattern = properties.getProperty("pictureNamePattern");
		input.close();
	}

	/**
	 * 转换函数,从word中提取内容并转换为MD.该函数应提取为单独对象的函数
	 * 
	 * @param file
	 *            doc文档文件
	 * @throws Exception
	 */
	private void doConvertToMD(File file) throws Exception {
		// 创建目录和文件
		String filename = file.getName();
		String docName = filename.substring(0, filename.lastIndexOf("."));
		File dstDir = new File(dstDirPath, docName);
		dstDir.mkdirs();
		File mdFile = new File(dstDir, docName + ".md");
		File picDir = new File(dstDir, picDirName);

		// 解析并获取doc解析的通用数据对象
		DocParser docParser = new DocParser(file);
		docParser.extractPicturesToFiles(picDir, picFilenamePattern);
		DocContent docContent = docParser.getDocContent();

		// 将通用数据对象内容转换为md格式
		MarkdownConverter mdConvertor = new MarkdownConverter(docContent);
		String mdString = mdConvertor.toMdString();

		// 输出到文件
		PrintWriter pw = new PrintWriter(mdFile, "UTF-8");
		pw.print(mdString);
		pw.flush();
		pw.close();
	}

	/**
	 * 程序入口
	 * 
	 * @param paths
	 *            路径参数,执行命令行时可直接输入多个文档文件路径进行转换
	 * @throws IOException
	 */
	public static void main(String[] paths) throws IOException {
		Transdoc transdoc = new Transdoc();
		if (paths.length > 0) {
			transdoc.prepareFiles(paths);
		} else {
			transdoc.prepareFromProperties();
		}
		transdoc.convertToMD();
	}

}
