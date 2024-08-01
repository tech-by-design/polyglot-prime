package lib.aide.resource.content;

import lib.aide.resource.Nature;

class ExceptionNature implements Nature {
    @Override
    public String mimeType() {
        return "text/plain";
    }
}