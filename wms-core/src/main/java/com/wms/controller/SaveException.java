package com.wms.controller;

public // 500에서 등록 상속받음
class SaveException extends RuntimeException {
    public  SaveException( String msg ){
        super(msg);
    }
}
