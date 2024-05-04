package backend;


import java.io.Serializable;

public class Response <T> implements Serializable {
    private Integer code;

    private String msg;

    private T data;

    private Response(Integer code, String msg) {
        this.code = code;
        this.msg = msg;
    }

    private Response(Integer code, String msg, T data) {
        this(code, msg);
        this.data = data;
    }

    public Integer getCode() {
        return code;
    }

    public String getMsg() {
        return msg;
    }

    public T getData() {
        return data;
    }

    public static <T> Response<T> success() {
        return new Response<>(200, "操作成功");
    }

    public static <T> Response<T> success(T data) {
        return new Response<>(200, "操作成功", data);
    }

    public static <T> Response<T> fail() {
        return new Response<>(400, "操作失败");
    }

    public static <T> Response<T> fail(String msg) {
        return new Response<>(400, msg);
    }

    public boolean isSuccess() {
        return this.code == 200;
    }

    public boolean isNotSuccess() {
        return this.code != 200;
    }
}

