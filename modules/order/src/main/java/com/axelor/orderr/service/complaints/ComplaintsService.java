package com.axelor.orderr.service.complaints;

import com.axelor.order.db.Complaints;

import java.util.List;

public interface ComplaintsService {
    List<Complaints> getCompList();
    Complaints saveComplaint(Complaints complaints);
}
