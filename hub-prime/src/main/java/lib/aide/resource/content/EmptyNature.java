package lib.aide.resource.content;

import lib.aide.resource.Nature;

class EmptyNature implements Nature {
    @Override
    public String mimeType() {
        return "text/plain";
    }
}