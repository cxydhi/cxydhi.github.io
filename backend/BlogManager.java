package backend;

import java.io.*;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Scanner;

public class BlogManager {
    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);
        System.out.println("步骤一==============创建新博客文章");
        System.out.print("请输入博客名称：");
        String articleName = scanner.nextLine(); //手撸Mybatis（一）——代理mapper

        String blogHexoDirectory = "D:\\javastudy\\yang-blog\\cxydhi.github.io";
        Response<ProcessResultDTO> newBlogResponse = buildArticle(blogHexoDirectory, articleName);
        if (newBlogResponse.isNotSuccess()) {
            System.out.println("新建博客失败===========" + newBlogResponse.getData());
            scanner.close();
            return;
        }
        System.out.println("Response:" + newBlogResponse.getData());

        System.out.println("步骤二==============导入博客信息");
        System.out.print("请输入导入的文件路径："); //"D:\\文档\\博客\\mybatis\\手撸Mybatis（一）——代理mapper.md"
        String importArticleFilePath = scanner.nextLine();
        String hexoArticleDirectory = blogHexoDirectory + File.separator + "source" + File.separator + "_posts";
        String hexoArticleFilePath = hexoArticleDirectory + File.separator + articleName + ".md";
        Response importArticleResponse = importArticle2Hexo(hexoArticleFilePath, importArticleFilePath);

        if (importArticleResponse.isNotSuccess()) {
            System.out.println("导入文件失败==========" + importArticleResponse.getData());
            scanner.close();
            return;
        }

        System.out.println("步骤三============替换图片");
        String imageStorePath = hexoArticleDirectory + File.separator + articleName;
        replaceImage(hexoArticleFilePath, imageStorePath);

        System.out.println("步骤四==========生成静态html文件");
        int year = Calendar.getInstance().get(Calendar.YEAR);
        int month = Calendar.getInstance().get(Calendar.MONTH) + 1;
        int day = Calendar.getInstance().get(Calendar.DAY_OF_MONTH);
        String accessUrl = String.format("localhost:4000/%4d/%02d/%02d/%s", year, month, day, articleName);
        System.out.println(accessUrl);
        generateStatics(blogHexoDirectory);

        scanner.close();
    }

    private static void generateStatics(String blogHexoDirectory) {
        String hexoInterceptor = "D:\\Program Files\\nodejs\\node_global\\hexo.cmd";
        File directory = new File(blogHexoDirectory);
        String[] commands = new String[]{hexoInterceptor, "d", "-g"};
        Response<ProcessResultDTO> response = ProcessUtils.process(directory, commands);
        System.out.println(response.getData());
    }

    private static void replaceImage(String hexoArticleFilePath, String imageStorePath) {
        // 当前图片编号
        int imageIndex = 1;
        List<String> imageLinkList = new ArrayList<>();
        try (BufferedReader bufferedReader = new BufferedReader(new FileReader(hexoArticleFilePath))) {
            String line = null;
            while ((line = bufferedReader.readLine()) != null) {
                line = line.trim();
                if (line.startsWith("![image.png]")) {
                    imageLinkList.add(line.substring("![image.png]".length() + 1, line.length() - 1));
                }
                if (line.startsWith("{% asset_img")) {
                    imageIndex ++;
                }
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        // 下载图片到本地
        List<String> newImageNameList = new ArrayList<>();
        for (String imageLink : imageLinkList) {
            String imageName = imageIndex + ".png";
            newImageNameList.add(imageName);
            String path = imageStorePath + File.separator + imageName;
            downloadPicture(imageLink, path);
            ++ imageIndex;
        }

        // 替换图片
        StringBuffer fileContent = new StringBuffer();
        int startIndex = 0;
        try (BufferedReader bufferedReader = new BufferedReader(new FileReader(hexoArticleFilePath))) {
            String line = null;
            while ((line = bufferedReader.readLine()) != null) {
                line = line.trim();
                if (line.startsWith("![image.png]")) {
                    fileContent.append("{% asset_img " + newImageNameList.get(startIndex++) + " %}")
                            .append("\n");
                } else {
                    fileContent.append(line).append("\n");
                }
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        try (BufferedWriter bufferedWriter = new BufferedWriter(new FileWriter(hexoArticleFilePath))) {
            bufferedWriter.write(fileContent.toString());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static void downloadPicture(String imageUrl,String path) {
        URL url = null;
        try {
            url = new URL(imageUrl);
            DataInputStream dataInputStream = new DataInputStream(url.openStream());
            FileOutputStream fileOutputStream = new FileOutputStream(new File(path));
            ByteArrayOutputStream output = new ByteArrayOutputStream();

            byte[] buffer = new byte[1024];
            int length;

            while ((length = dataInputStream.read(buffer)) > 0) {
                output.write(buffer, 0, length);
            }
            fileOutputStream.write(output.toByteArray());//Base64转换成文件下载到本地
            dataInputStream.close();
            fileOutputStream.close();
        } catch (MalformedURLException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }



    private static Response importArticle2Hexo(String hexoArticleFilePath, String importArticleFilePath) {
        // 尾加到文件末尾
        try (FileInputStream fileInputStream = new FileInputStream(importArticleFilePath);
             FileOutputStream fileOutputStream = new FileOutputStream(hexoArticleFilePath, true)) {
            byte[] buffer = new byte[4 * 1024];
            int len = -1;
            while ((len = fileInputStream.read(buffer)) != - 1) {
                fileOutputStream.write(buffer, 0, len);
            }
            return Response.success();
        } catch (IOException e) {
            e.printStackTrace();
            return Response.fail(e.getMessage());
        }
    }

    private static Response<ProcessResultDTO> buildArticle(String blogHexDirectory, String articleName) {
        String hexoInterceptor = "D:\\Program Files\\nodejs\\node_global\\hexo.cmd";
        File directory = new File(blogHexDirectory);
        String[] commands = new String[]{hexoInterceptor, "new", articleName};
        Response<ProcessResultDTO> response = ProcessUtils.process(directory, commands);
        return response;
    }
}
