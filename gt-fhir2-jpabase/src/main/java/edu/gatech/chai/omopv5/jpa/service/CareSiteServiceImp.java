package edu.gatech.chai.omopv5.jpa.service;

import java.util.List;

import javax.persistence.EntityManager;
import javax.persistence.TypedQuery;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import edu.gatech.chai.omopv5.jpa.dao.CareSiteDao;
import edu.gatech.chai.omopv5.jpa.entity.CareSite;
import edu.gatech.chai.omopv5.jpa.entity.Location;

@Service
public class CareSiteServiceImp extends BaseEntityServiceImp<CareSite, CareSiteDao> implements CareSiteService{
	
	public CareSiteServiceImp() {
		super(CareSite.class);
	}
	
	@Transactional(readOnly = true)
	@Override
	public CareSite searchByLocation(Location location) {
		EntityManager em = getEntityDao().getEntityManager();
		String query = "SELECT t FROM CareSite t WHERE location_id like :value:";
		List<? extends CareSite> results = em.createQuery(query, CareSite.class)
				.setParameter("value",location.getId()).getResultList();
		if (results.size() > 0) {
			return results.get(0);
		} else
			return null;
	}

	@Transactional(readOnly = true)
	@Override
	public CareSite searchByNameAndLocation(String careSiteName, Location location) {
		EntityManager em = getEntityDao().getEntityManager();
		String queryString = "SELECT t FROM CareSite t WHERE";
		
		// Construct where clause here.
		String where_clause = "";
		if (careSiteName != null)  {
			where_clause = "careSiteName like :cName";
		}
		
		if (location != null) {
			if (where_clause == "") where_clause = "location = :location";
			else where_clause += " AND location = :location";
		}
		
		queryString += " "+where_clause;
		System.out.println("Query for FPerson"+queryString);
		
		TypedQuery<? extends CareSite> query = em.createQuery(queryString, CareSite.class);
		if (careSiteName != null) query = query.setParameter("cName", careSiteName);
		if (location != null) query = query.setParameter("location", location);
		
		System.out.println("cName:"+careSiteName);
		List<? extends CareSite> results = query.getResultList();
		if (results.size() > 0) {
			return results.get(0);
		} else {
			return null;	
		}
	}

}
