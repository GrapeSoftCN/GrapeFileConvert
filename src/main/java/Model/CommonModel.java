package Model;

import java.text.SimpleDateFormat;
import java.util.Date;

import org.json.simple.JSONObject;

import apps.appsProxy;
import nlogger.nlogger;
import string.StringHelper;

public class CommonModel {
    /**
     * 获取配置信息
     * 
     * @param key
     * @return
     */
    public String getOtherConfig(String key) {
        String value = "";
        try {
            JSONObject object = appsProxy.configValue();
            if (object != null && object.size() > 0) {
                object = JSONObject.toJSON(object.getString("other"));
            }
            if (object != null && object.size() > 0) {
                value = object.getString(key);
            }
        } catch (Exception e) {
            nlogger.logout(e);
            value = null;
        }
        return value;
    }

    /**
     * 获取当前日期
     * 
     * @return 年-月-日
     */
    public String getDate() {
        Date currentTime = new Date();
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd");
        String dateString = format.format(currentTime);
        return dateString;
    }

    /**
     * 获取文件相对路径
     * 
     * @param output
     * @return
     */
    public String getFileUrl(String destFile) {
        int i = 0;
        if (StringHelper.InvaildString(destFile)) {
            if (destFile.contains("File//upload")) {
                i = destFile.toLowerCase().indexOf("file//upload");
                destFile = "//" + destFile.substring(i);
            }
            if (destFile.contains("File\\upload")) {
                i = destFile.toLowerCase().indexOf("file\\upload");
                destFile = "//" + destFile.substring(i);
            }
            if (destFile.contains("File/upload")) {
                i = destFile.toLowerCase().indexOf("file/upload");
                destFile = "//" + destFile.substring(i);
            }
        }
        return destFile;
    }

    /**
     * 获取文件完整路径
     * 
     * @param path
     *            1:以盘符开头;2:以/开头；3：以http://开头;4文件相对路径，不以/开头
     * @return
     */
    public String getFullPath(String path) {
        String dir = getOtherConfig("input");
        if (StringHelper.InvaildString(path)) {
            if (path.startsWith("/")) {
                path = dir + path;
            }
            if (path.startsWith("http://")) {
                path = dir + getFileUrl(path);
            }
            if (!path.contains(":") && !path.startsWith("/")) {
                path = dir + "//" + path;
            }
        }
        return path;
    }
}
