package com.axelor.orderr.service.complaints;

import com.axelor.order.db.Complaints;
import com.axelor.order.db.repo.ComplaintsRepository;
import com.google.inject.Inject;
import com.google.inject.persist.Transactional;

import java.util.List;

public class ComplaintsServiceImpl implements ComplaintsService{

    private final ComplaintsRepository repo;

    @Inject
    public ComplaintsServiceImpl(ComplaintsRepository repo) {
        this.repo = repo;
    }

    @Override
    public List<Complaints> getCompList() {
        return repo.all().fetch();
    }

    @Override
    @Transactional(rollbackOn = {Exception.class})
    public Complaints saveComplaint(Complaints complaints) {
        return repo.save(complaints);
    }
}
