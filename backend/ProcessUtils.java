package backend;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;

public class ProcessUtils {
    public static Response<ProcessResultDTO> process(File directory, String... command) {
        try {
            ProcessBuilder processBuilder = new ProcessBuilder(command);
            if (directory != null) {
                processBuilder.directory(directory);
            }
            Process process = processBuilder.start();
            // 读取标准输出和错误输出
            InputStream inputStream = process.getInputStream();
            InputStream errorStream = process.getErrorStream();
            // 等待进程结束
            process.waitFor();

            ProcessResultDTO processResultDTO = new ProcessResultDTO();
            processResultDTO.setResultMsg(convert2String(inputStream));
            processResultDTO.setErrorMsg(convert2String(errorStream));
            return Response.success(processResultDTO);
        } catch (Exception e) {
            e.printStackTrace();
            return Response.fail(e.getMessage());
        }
    }

    private static String convert2String(InputStream inputStream) {
        try (BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(inputStream))) {
            StringBuilder stringBuilder = new StringBuilder();
            String line = null;
            while ((line = bufferedReader.readLine()) != null) {
                stringBuilder.append(line);
            }
            return stringBuilder.toString();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }
}
