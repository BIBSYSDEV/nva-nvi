package no.sikt.nva.nvi.index.model.document;

public record Pages(String begin,
                    String end,
                    String total) {

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private String begin;
        private String end;

        private String total;

        private Builder() {
        }

        public Builder withBegin(String begin) {
            this.begin = begin;
            return this;
        }

        public Builder withEnd(String end) {
            this.end = end;
            return this;
        }

        public Builder withTotal(String total) {
            this.total = total;
            return this;
        }

        public Pages build() {
            return new Pages(begin, end, total);
        }
    }
}
