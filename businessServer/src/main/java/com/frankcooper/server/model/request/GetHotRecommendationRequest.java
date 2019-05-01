package com.frankcooper.server.model.request;

//获取当前最热的电影
public class GetHotRecommendationRequest {

    private int sum;

    public GetHotRecommendationRequest(int sum) {
        this.sum = sum;
    }

    public int getSum() {
        return sum;
    }

    public void setSum(int sum) {
        this.sum = sum;
    }
}
