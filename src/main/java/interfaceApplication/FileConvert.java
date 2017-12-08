package interfaceApplication;

import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.jodconverter.OfficeDocumentConverter;

import JGrapeSystem.rMsg;
import Model.CommonModel;
import nlogger.nlogger;
import offices.ThirfOOS;
import security.codec;
import string.StringHelper;
import time.TimeHelper;

/**
 * 文件转换 {"db":"mongodb","other":{"input":"G://Image","output":
 * "G://Image//File//upload"},"cache":"redis"}
 *
 */
public class FileConvert {
    private static AtomicInteger fileNO = new AtomicInteger(0);
    private CommonModel model = new CommonModel();

    private String getUnqueue() {
        return (new Integer(fileNO.incrementAndGet())).toString();
    }

    /**
     * 文件转换
     * 
     * @param inputFile
     *            待转换文件路径
     * @param imagesuffix
     *            设置上传的ppt转图片的图片路径
     * @param type
     *            转换类型： 1：office转换成pdf； 2：office转换成html；
     *            3：office转换成html并获取html内容； 4：office文件转图片
     * 
     * @param mode
     *            获取的html内容样式 0：原样输出； 1：获取调整后html内容
     * @return
     */
    public String Convert(String inputFile, String imagesuffix, int type, int mode) {
        inputFile = codec.DecodeFastJSON(inputFile);
        String result = rMsg.netMSG(100, "文件转换失败");
        inputFile = model.getFullPath(inputFile); // 获取完整路径
        if (!StringHelper.InvaildString(inputFile)) {
            return rMsg.netMSG(1, "文件位置错误");
        }
        File input = new File(inputFile);
        if (!input.exists()) { // 文件不存在
            return rMsg.netMSG(1, "文件不存在");
        }
        switch (type) {
        case 1: // 转换成pdf
            result = officeConvert(inputFile, ".pdf");
            break;
        case 2: // 转换成html
            result = officeConvert(inputFile, ".html");
            break;
        case 3: // 转换成html,并获取html文件内容
            result = getHtmlString(inputFile, mode);
            break;
        case 4: // pdf文件转换为图片
            result = pdf2Jpgs(inputFile, imagesuffix);
            break;
        }
        return result;
    }

    /**
     * 根据不同的扩展名，选择转换成不同文件
     * 
     * @param sourceFile
     * @param ext
     * @return
     */
    private String officeConvert(String sourceFile, String ext) {
        File outputFile = null;
        try {
            File inputFile = new File(sourceFile);
            if (!inputFile.exists()) {
                return rMsg.netMSG(1, "文件不存在");
            }
            String outFile = getOutputFile();
            outFile = outFile + "//" + TimeHelper.nowMillis() + getUnqueue() + ext;
            outputFile = new File(outFile);
            OfficeDocumentConverter odc = new ThirfOOS().getConverter();
            if (odc == null) {
                return rMsg.netMSG(100, "文件转换失败");
            }
            odc.convert(inputFile, outputFile);
        } catch (Exception e) {
            nlogger.logout(e);
            if (outputFile != null && outputFile.exists()) {
                outputFile.delete();
            }
            return rMsg.netMSG(100, "文件转换失败");
        }
        return model.getFileUrl(outputFile.toString());
    }

    /**
     * 获取html内容
     * 
     * @param source
     * @param mode
     *            mode为1，输出调整后的数据
     * @return
     */
    private String getHtmlString(String source, int mode) {
        String result = office2htmlString(source);
        if (result.contains("errorcode")) {
            return result;
        }
        switch (mode) {
        case 1: // 调整后输出
            result = clearFormat(result);
            break;
        }
        return result;
    }

    /**
     * 获取html文件内容
     * 
     * @param sourceFile
     * @return
     */
    private String office2htmlString(String sourceFile) {
        String result = "";
        String ffilepath = officeConvert(sourceFile, ".html");
        if (ffilepath.contains("errorcode")) {
            return ffilepath;
        }
        File htmlFile = new File(ffilepath);
        // 获取html文件流
        StringBuffer html = new StringBuffer();
        BufferedReader br = null;
        try {
            br = new BufferedReader(new InputStreamReader(new FileInputStream(htmlFile)));
            while (br.ready()) {
                html.append(br.readLine());
            }
            br.close();
        } catch (FileNotFoundException e) {
            result = rMsg.netMSG(2, "文件不存在");
            e.printStackTrace();
        } catch (IOException e) {
            result = rMsg.netMSG(3, "当前格式不支持");
            e.printStackTrace();
        } finally {
            if (br != null) {
                try {
                    br.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            if (htmlFile.exists()) {
                htmlFile.delete();
            }
        }
        result = html.toString();
        return result;
    }

    /**
     * 调整获取到的html内容
     * 
     * @param htmlStr
     * @return
     */
    private String clearFormat(String htmlStr) {
        if (!StringHelper.InvaildString(htmlStr)) {
            return htmlStr;
        }
        String Date = model.getDate();
        String filepath = model.getOtherConfig("weburl") + "/File/upload/" + Date; // html中包含图片的地址
        // 获取body内容的正则
        String bodyReg = "<BODY .*</BODY>";
        Pattern bodyPattern = Pattern.compile(bodyReg);
        Matcher bodyMatcher = bodyPattern.matcher(htmlStr);
        if (bodyMatcher.find()) {
            // 获取BODY内容，并转化BODY标签为DIV
            htmlStr = bodyMatcher.group().replaceFirst("<BODY", "<DIV").replaceAll("</BODY>", "</DIV>");
        }
        // 调整图片地址
        htmlStr = htmlStr.replaceAll("<img src=\"", "<img src=\"" + filepath + "/");
        htmlStr = htmlStr.replaceAll("<IMG SRC=\"", "<IMG SRC=\"" + filepath + "/");
        // 把<P></P>转换成</div></div>保留样式
        htmlStr = htmlStr.replaceAll("(<P)([^>]*>.*?)(<\\/P>)", "<div$2</div>");
        // 把<P></P>转换成</div></div>并删除样式
        // 删除不需要的标签
        htmlStr = htmlStr.replaceAll("<[/]?(font|FONT|span|SPAN|xml|XML|del|DEL|ins|INS|meta|META|[ovwxpOVWXP]:\\w+)[^>]*?>", "");
        // 删除不需要的属性

        // htmlStr = htmlStr.replaceAll(
        // "<([^>]*)(?:lang|LANG|class|CLASS|style|STYLE|size|SIZE|face|FACE|[ovwxpOVWXP]:\\w+)=(?:'[^']*'|\"\"[^\"\"]*\"\"|[^>]+)([^>]*)>",
        // "<$1$2>");
        return htmlStr;
    }

    /**
     * 上传ppt转换为图片
     * 
     * @param sourceFile  待转换文件
     * @param imageSuffix  图片扩展名称
     * @return   
     */
    @SuppressWarnings({ "resource", "rawtypes" })
    private String pdf2Jpgs(String inputFile, String imageSuffix) {
        File file = null;
        String result = rMsg.netMSG(100, "转换失败");
        if (inputFile.contains("errorcode")) {
            return inputFile;
        }
        try {
             String outputFile = getOutputFile();
             if (!new File(outputFile).exists()) {
                new File(outputFile).mkdirs();
            }
            String sourceFile = officeConvert(inputFile, ".pdf"); // 转换为pdf文件
            if (!StringHelper.InvaildString(sourceFile)) {
                return rMsg.netMSG(1, "文件转换失败");
            }
            if (sourceFile.contains("errorcode")) {
                return sourceFile;
            }
            sourceFile = model.getFullPath(sourceFile);  //补充文件路径
            String FileName = getFileName(sourceFile);
            outputFile = outputFile + "//" + FileName;
            file = new File(sourceFile);
            PDDocument pdDocument = new PDDocument();
            pdDocument = PDDocument.load(file);
            int size = pdDocument.getNumberOfPages();
            List<String> list = new ArrayList<String>();
            for (int i = 0; i < size; i++) {
                BufferedImage image = new PDFRenderer(pdDocument).renderImage(i, 1, ImageType.RGB);
                Iterator iter = ImageIO.getImageWritersBySuffix(imageSuffix);
                ImageWriter writer = (ImageWriter) iter.next();
                File outFile = new File(outputFile + "_" + (i + 1) + "." + imageSuffix);
                FileOutputStream out = new FileOutputStream(outFile);
                ImageOutputStream outImage = ImageIO.createImageOutputStream(out);
                writer.setOutput(outImage);
                writer.write(new IIOImage(image, null, null));
                list.add(model.getFileUrl(outFile.toString()));
            }
            result = StringHelper.join(list);
        } catch (Exception e) {
            nlogger.logout(e);
            result = rMsg.netMSG(100, "转换失败");
        }
        return result;
    }

    /**
     * 获取输出文件路径
     * 
     * @param ext
     *            文件扩展名
     * @return G://Image//File//upload//2017-12-08//filename.extname
     */
    private String getOutputFile() {
        String destFile = model.getOtherConfig("output");
        String date = model.getDate();
        destFile = destFile + "//" + date;
        File file = new File(destFile);
        if (!file.exists()) {
            file.mkdirs();
        }
        return destFile;
    }

    /**
     * 获取文件名称
     * 
     * @param path
     * @return
     */
    private String getFileName(String path) {
        String FileName = "";
        int i = 0;
        if (StringHelper.InvaildString(path)) {
            if (path.contains("/")) {
                if (path.contains("//")) {
                    i = path.lastIndexOf("//") + 2;
                } else {
                    i = path.lastIndexOf("/") + 1;
                }
            }
            if (path.contains("\\")) {
                i = path.lastIndexOf("\\") + 2;
            }
            FileName = path.substring(i, path.lastIndexOf("."));
        }
        return FileName;
    }
}
