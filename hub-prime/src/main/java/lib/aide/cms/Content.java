package lib.aide.cms;

public interface Content<N extends Nature, C> {
    N nature();
    C content();    
}
