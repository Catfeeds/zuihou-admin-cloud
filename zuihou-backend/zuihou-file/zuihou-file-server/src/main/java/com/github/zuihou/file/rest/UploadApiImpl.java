package com.github.zuihou.file.rest;

import com.github.zuihou.base.Result;
import com.github.zuihou.commons.context.BaseContextHandler;
import com.github.zuihou.file.config.FileProperties;
import com.github.zuihou.file.entity.file.po.ZhFile;
import com.github.zuihou.file.repository.file.service.FileService;
import com.github.zuihou.file.rest.dozer.DozerUtils;
import com.github.zuihou.file.rest.file.api.UploadApi;
import com.github.zuihou.file.rest.file.constant.FileType;
import com.github.zuihou.file.rest.file.dto.UploadFileDTO;
import com.github.zuihou.file.rest.file.dto.UploadListDTO;
import com.github.zuihou.file.support.FileModel;
import com.github.zuihou.file.utils.FileUtils;
import com.github.zuihou.file.utils.UploadUtil;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiImplicitParam;
import io.swagger.annotations.ApiImplicitParams;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.Part;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * api上传文件接口， 跟FileApiImpl的不同之处在于这里提供给前端接口
 *
 * @author zuihou
 * @createTime 2018-01-27 20:00
 */
@Api(value = "API - UploadApiImpl", description = "文件管理")
@Slf4j
@RestController
@RequestMapping("upload")
public class UploadApiImpl implements UploadApi {
    private final static DateTimeFormatter DTF = DateTimeFormatter.ofPattern("yyyy/MM");
    @Autowired
    private FileService fileService;
    @Autowired
    private DozerUtils dozerUtils;
    @Autowired
    private FileProperties fileProperties;


    /**
     * 上传文件(支持批量)
     * * 1，先将文件存在本地,并且生成文件名
     * * 2，然后在上传到fastdfs
     * <p>
     * 还没找到swagger 如何支持 多文件上传的配置。 有解决方法的朋友给我留言
     */
    @ApiOperation(value = "文件上传", notes = "文件上传")
    @ApiImplicitParams({
            @ApiImplicitParam(name = "file0", value = "文件1", dataType = "string", paramType = "query"),
            @ApiImplicitParam(name = "file1", value = "文件2", dataTypeClass = MultipartFile.class, paramType = "query")
    })
    @RequestMapping(value = "multi", method = RequestMethod.POST)
    public Result<UploadListDTO> uploadMulti(HttpServletRequest request) {
        try {
            String appId = BaseContextHandler.getAppId();
            String userName = BaseContextHandler.getUserName();
            // Servlet3.0方式上传文件
            Collection<Part> parts = request.getParts();

            //1，先将文件存在本地,并且生成文件名
            List<ZhFile> zhFileList = new ArrayList<>();
            for (Part part : parts) {
                // 忽略路径字段,只处理文件类型
                if (part.getContentType() != null) {
                    //文件名
                    String submittedFileName = part.getSubmittedFileName();
                    //后缀
                    String suffix = FileUtils.getExtension(submittedFileName);
                    //生成文件名
                    String fileName = UUID.randomUUID().toString() + suffix;

                    try {
                        //日期文件夹
                        String secDir = LocalDate.now().format(DTF);
                        // /home/zuihou/APP_ID/YYYY/MM
                        String relativePath = Paths.get(appId, secDir).toString();
                        String absolutePath = Paths.get(fileProperties.getUploadPathPrefix(), relativePath).toString();

                        //存到web服务器
                        FileUtils.write(part.getInputStream(), absolutePath, fileName);

                        //上传到fastdfs 并且返回 访问 url
                        FileModel fileModel = UploadUtil.remove2DFS(appId, "U1",
                                fileProperties, absolutePath, relativePath, fileName);

                        ZhFile zhFile = dozerUtils.map(fileModel, ZhFile.class);
                        zhFile.setAppId(appId);
                        zhFile.setCreateName(userName);
                        zhFile.setUpdateName(userName);
                        zhFile.setIcon("");
                        zhFile.setType(FileType.API.toString());
                        zhFileList.add(zhFile);
                    } catch (Exception e) {
                        log.error("保存文件到服务器临时目录失败:", e);
                    }
                }

                //3,存储
                fileService.save(zhFileList);

                //4,转换
                List<UploadFileDTO> list = dozerUtils.mapList(zhFileList, UploadFileDTO.class);
                UploadListDTO uploadListDTO = new UploadListDTO();
                uploadListDTO.setList(list);
                return Result.success(uploadListDTO);
            }
            return Result.success(null);
        } catch (Exception e) {
            return Result.fail(e);
        }
    }

}
