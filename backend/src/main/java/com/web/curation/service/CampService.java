package com.web.curation.service;

import com.web.curation.data.dto.CampDto;
import com.web.curation.data.dto.ScheduleDto;
import com.web.curation.data.dto.SearchListDto;
import com.web.curation.data.dto.TagDto;
import com.web.curation.data.entity.CampTag;
import com.web.curation.data.entity.LikedCampList;
import com.web.curation.data.entity.TotalCampList;
import com.web.curation.data.entity.User;
import com.web.curation.data.repository.CampRepository;
import com.web.curation.data.repository.LikedCampRepository;
import com.web.curation.data.repository.TagRepository;
import com.web.curation.data.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.persistence.EntityManager;
import javax.persistence.criteria.*;
import java.util.*;
import java.util.stream.Collectors;


@Service
public class CampService{
    private final CampRepository campRepository;
    private final LikedCampRepository likedCampRepository;
    private final UserRepository userRepository;
    private final TagRepository tagRepository;
    EntityManager entityManager;

    @Autowired
    public CampService(CampRepository campRepository, LikedCampRepository likedCampRepository, UserRepository userRepository, TagRepository tagRepository) {
        this.campRepository = campRepository;
        this.likedCampRepository = likedCampRepository;
        this.userRepository = userRepository;
        this.tagRepository = tagRepository;
    }

    public List<CampDto.CampList> filterCampList(SearchListDto.SearchList searchList) {

        List<TotalCampList> totalCampLists = new ArrayList<>();

        if(searchList.getKeyword()==null && searchList.getTags().isEmpty() && searchList.getGugun()==null && searchList.getSido()==null){
            if(searchList.getArrange() == 0) {
                totalCampLists = campRepository.findAll(Sort.by(Sort.Direction.ASC,"facltNm"));
            }else{
                totalCampLists = campRepository.findAll();
            }

        }else{
            totalCampLists = campRepository.findAll(searchCamps(searchList));
        }

        List<CampDto.CampList> filterCampList = new ArrayList<>();
        Pageable pageable = PageRequest.of(searchList.getPage(), 10);
        final int start = (int)pageable.getOffset();
        final int end = Math.min((start + pageable.getPageSize()), totalCampLists.size());
        Page<TotalCampList> ptl = new PageImpl<>(totalCampLists.subList(start,end),pageable ,totalCampLists.size());

        for (TotalCampList cl : ptl){
            CampDto.CampList tcl = new CampDto.CampList(cl);
            filterCampList.add(tcl);
        }

//        CampDto.CampList last = new CampDto.CampList();
//        last.setCampId(-1);
//        last.setFacltNm("last");
//        last.setAddr1("last");
//        last.setFirstImageUrl("last");
//        last.setHomepage("last");
//        last.setMapX("last");
//        last.setMapY("last");
//
//        if ((start + pageable.getPageSize()) >= totalCampLists.size()){
//            filterCampList.add(last);
//        }

        return filterCampList;

    }


    public Specification<TotalCampList> searchCamps(SearchListDto.SearchList searchList){
        return ((cl, criteriaQuery, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();

            if(searchList.getKeyword() != null){
                String keyword = "%"+searchList.getKeyword()+"%";
                Predicate keywordSearch = criteriaBuilder.like(cl.get("facltNm"),keyword);
                predicates.add(keywordSearch);
            }

            if(searchList.getSido() != null && searchList.getGugun() != null){
                String sido = searchList.getSido()+"%";
                String sigungu = searchList.getGugun()+"%";
                Predicate regionSearchdoNm = criteriaBuilder.like(cl.get("doNm"),sido);
                Predicate regionSearchsigungu = criteriaBuilder.like(cl.get("sigunguNm"),sigungu);
                Predicate regionSearch = criteriaBuilder.and(regionSearchdoNm,regionSearchsigungu);
                predicates.add(regionSearch);
            }

            if(!searchList.getTags().isEmpty()){
                Subquery<Integer> subquery = criteriaQuery.subquery(Integer.class);
                Root<CampTag> s = subquery.from(CampTag.class);
                Join<TotalCampList, CampTag> ts = s.join("totalCampList");
                subquery.select(ts.get("campId")).distinct(true).where(s.get("hashtag").in(searchList.getTags()));
                Predicate tagSearch = cl.get("campId").in(subquery);
                predicates.add(tagSearch);
            }

            if(searchList.getArrange() == 0){
                criteriaQuery.orderBy(criteriaBuilder.asc(cl.get("facltNm")));  // 가나다순 정렬
            }else if(searchList.getArrange() == 1){
                criteriaQuery.orderBy(criteriaBuilder.desc(cl.get("lclcount"))); // 인기순 정렬
            }

            return criteriaBuilder.and(predicates.toArray(new Predicate[0]));
        });
    }

    /* best camping */
    public List<String> getBestCamps(){
        List<TotalCampList> bestcamp = likedCampRepository.findTopSelectedList(PageRequest.of(0,4));
        List<String> bestcampname = new ArrayList<>();
        for ( TotalCampList b : bestcamp) {
            bestcampname.add(campRepository.getByCampId(b.getCampId()).getFacltNm());
        }

        return bestcampname;
    }


    /* campList READ */
    @Transactional(readOnly = true)
    public List<CampDto.CampList> getAllCamps(int page) {
        PageRequest pageRequest = PageRequest.of(page, 10);
        Page<TotalCampList> totalCampList = campRepository.findAll(pageRequest);
        List<CampDto.CampList> rttotalCampList = new ArrayList<>();
        for(TotalCampList tcl : totalCampList){
            CampDto.CampList cl = new CampDto.CampList(tcl);
            rttotalCampList.add(cl);
        }
        return rttotalCampList;
    }

    public List<TotalCampList> getAllCamps() {
        List<TotalCampList> totalCampList = campRepository.findAll();
        return totalCampList;
    }


    /* campDetail READ */
    @Transactional(readOnly = true)
    public CampDto.CampDetail campDetailRead(int campId) {
        TotalCampList totalCampList = campRepository.findById(campId).orElseThrow(() ->
                new IllegalArgumentException("해당 캠핑장이 존재하지 않습니다. id: " + campId));

        return new CampDto.CampDetail(totalCampList);
    }

     /* CREATE */
    @Transactional
    public int save(ScheduleDto.Request dto, String email, int campId) {
        /* User 정보를 가져와 dto에 담아준다. */
        User user = userRepository.getByEmail(email); // email로?
        dto.setUserId(user);
        TotalCampList camp = campRepository.getById(campId);
        int lclcount = camp.getLclcount();
        camp.setLclcount(lclcount+1);
        dto.setCampId(camp);

        LikedCampList likedCampList = dto.toEntity();
        likedCampRepository.save(likedCampList);

        return likedCampList.getSaveId();
    }

}
