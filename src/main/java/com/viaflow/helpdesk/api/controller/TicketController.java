package com.viaflow.helpdesk.api.controller;

import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

import javax.servlet.http.HttpServletRequest;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.BindingResult;
import org.springframework.validation.ObjectError;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.viaflow.helpdesk.api.dto.Summary;
import com.viaflow.helpdesk.api.entity.ChangeStatus;
import com.viaflow.helpdesk.api.entity.Ticket;
import com.viaflow.helpdesk.api.entity.User;
import com.viaflow.helpdesk.api.enums.ProfileEnum;
import com.viaflow.helpdesk.api.enums.StatusEnum;
import com.viaflow.helpdesk.api.response.Response;
import com.viaflow.helpdesk.api.security.jwt.JwtTokenUtil;
import com.viaflow.helpdesk.api.service.TicketService;
import com.viaflow.helpdesk.api.service.UserService;

@RestController
@RequestMapping(value = "/api/ticket")
@CrossOrigin(origins = "*")
public class TicketController {

	@Autowired
	private TicketService ticketService;

	@Autowired
	protected JwtTokenUtil jwtTokenUtil;

	@Autowired
	private UserService userService;

	@PostMapping
	@PreAuthorize("hasAnyRole('CUSTOMER')")
	public ResponseEntity<Response<Ticket>> create(HttpServletRequest req, @RequestBody Ticket ticket,
			BindingResult result) {
		Response<Ticket> response = new Response<>();
		try {
			validaCreateTicket(ticket, result);
			if (result.hasErrors()) {
				result.getAllErrors().forEach(error -> {
					response.getErrors().add(error.getDefaultMessage());
				});
				return ResponseEntity.badRequest().body(response);
			}

			ticket.setStatus(StatusEnum.New);
			ticket.setUser(userFromRequest(req));
			ticket.setDate(new Date());
			ticket.setNumber(generateNumber());
			Ticket persistedTicket = ticketService.createOrUpdate(ticket);
			response.setData(persistedTicket);

		} catch (Exception e) {
			response.getErrors().add("Critical Error! " + e.getMessage());
			return ResponseEntity.badRequest().body(response);
		}
		return ResponseEntity.ok(response);
	}

	@PutMapping
	@PreAuthorize("hasAnyRole('CUSTOMER')")
	public ResponseEntity<Response<Ticket>> update(HttpServletRequest req, @RequestBody Ticket ticket,
			BindingResult result) {
		Response<Ticket> response = new Response<>();
		try {
			validaUpdateTicket(ticket, result);
			if (result.hasErrors()) {
				result.getAllErrors().forEach(error -> {
					response.getErrors().add(error.getDefaultMessage());
				});
				return ResponseEntity.badRequest().body(response);
			}
			Ticket oldTicket = ticketService.findById(ticket.getId());
			ticket.setStatus(oldTicket.getStatus());
			ticket.setUser(oldTicket.getUser());
			ticket.setDate(oldTicket.getDate());
			ticket.setNumber(oldTicket.getNumber());
			if (ticket.getAssignedUser() != null) {
				ticket.setAssignedUser(oldTicket.getAssignedUser());
			}

			Ticket persistedTicket = ticketService.createOrUpdate(ticket);
			response.setData(persistedTicket);

		} catch (Exception e) {
			response.getErrors().add("Critical Error! " + e.getMessage());
			return ResponseEntity.badRequest().body(response);
		}
		return ResponseEntity.ok(response);
	}

	@GetMapping(value = "{id}")
	@PreAuthorize("hasAnyRole('CUSTOMER', 'TECHNICIAN')")
	public ResponseEntity<Response<Ticket>> findById(@PathVariable("id") String id) {
		Response<Ticket> response = new Response<>();
		Ticket ticket = ticketService.findById(id);
		if (ticket == null) {
			response.getErrors().add("Ticket not found : " + id);
			return ResponseEntity.badRequest().body(response);
		}
		List<ChangeStatus> changes = new ArrayList<ChangeStatus>();
		Iterable<ChangeStatus> changesCurrent = ticketService.listChangeStatus(ticket.getId());
		changesCurrent.forEach(item -> {
			item.setTicket(null);
			changes.add(item);
		});
		ticket.setChanges(changes);
		response.setData(ticket);
		return ResponseEntity.ok(response);
	}

	@GetMapping(value = "{page}/{count}")
	@PreAuthorize("hasAnyRole('CUSTOMER', 'TECHNICIAN')")
	public ResponseEntity<Response<Page<Ticket>>> findAll(HttpServletRequest req, @PathVariable("page") int page,
			@PathVariable("count") int count) {
		Response<Page<Ticket>> response = new Response<>();
		Page<Ticket> tickets = null;

		User userRequest = userFromRequest(req);
		if (userRequest.getProfile().equals(ProfileEnum.ROLE_TECHNICIAN)) {
			tickets = ticketService.listTicket(page, count);
		} else if (userRequest.getProfile().equals(ProfileEnum.ROLE_CUSTOMER)) {
			tickets = ticketService.findByCurrentUser(page, count, userRequest.getId());
		}

		response.setData(tickets);
		return ResponseEntity.ok(response);
	}

	@GetMapping(value = "{page}/{count}/{number}/{title}/{status}/{priority}/{assigned}")
	@PreAuthorize("hasAnyRole('CUSTOMER', 'TECHNICIAN')")
	public ResponseEntity<Response<Page<Ticket>>> findByParams(HttpServletRequest req, @PathVariable("page") int page,
			@PathVariable("count") int count, @PathVariable("number") int number, @PathVariable("title") String title,
			@PathVariable("status") String status, @PathVariable("priority") String priority,
			@PathVariable("assigned") boolean assigned) {
		Response<Page<Ticket>> response = new Response<>();
		title = title.equals("uninformed") ? "" : title;
		status = status.equals("uninformed") ? "" : status;
		priority = priority.equals("uninformed") ? "" : priority;
		Page<Ticket> tickets = null;

		if (number > 0) {
			tickets = ticketService.findByNumber(page, count, number);
		} else {
			User user = userFromRequest(req);
			if (user.getProfile().equals(ProfileEnum.ROLE_TECHNICIAN)) {
				if (assigned) {
					tickets = ticketService.findByParametersAndAssignedUser(page, count, title, status, priority,
							user.getId());
				} else {
					tickets = ticketService.findByParameters(page, count, title, status, priority);
				}

			} else if (user.getProfile().equals(ProfileEnum.ROLE_CUSTOMER)) {
				tickets = ticketService.findByParametersAndCurrentUser(page, count, title, status, priority,
						user.getId());
			}
		}

		response.setData(tickets);
		return ResponseEntity.ok(response);
	}

	@PutMapping(value = "{id}/{status}")
	@PreAuthorize("hasAnyRole('CUSTOMER', 'TECHNICIAN')")
	public ResponseEntity<Response<Ticket>> changeStatus(@PathVariable("id") String id,
			@PathVariable("status") String status, HttpServletRequest req, @RequestBody Ticket ticket,
			BindingResult result) {
		Response<Ticket> response = new Response<>();

		try {
			validaChangeStatusTicket(id, status, result);
			if (result.hasErrors()) {
				result.getAllErrors().forEach(error -> {
					response.getErrors().add(error.getDefaultMessage());
				});
				return ResponseEntity.badRequest().body(response);
			}
			Ticket oldticket = ticketService.findById(id);
			oldticket.setStatus(StatusEnum.getStatus(status));
			if (status.equals("Assigned")) {
				oldticket.setAssignedUser(userFromRequest(req));
			}

			Ticket newTicket = ticketService.createOrUpdate(oldticket);
			ChangeStatus changeStatus = new ChangeStatus();
			changeStatus.setUserChange(userFromRequest(req));
			changeStatus.setDateChangeStatus(new Date());
			changeStatus.setStatus(StatusEnum.getStatus(status));
			changeStatus.setTicket(newTicket);
			ticketService.createChangeStatus(changeStatus);

			response.setData(newTicket);

		} catch (Exception e) {
			response.getErrors().add("Critical Error! " + e.getMessage());
			return ResponseEntity.badRequest().body(response);
		}

		return ResponseEntity.ok(response);
	}

	@GetMapping(value = "/summary")
	public ResponseEntity<Response<Summary>> findSummary() {
		Response<Summary> response = new Response<>();
		Summary summary = new Summary();
		int amountNew = 0;
		int amountResolved = 0;
		int amountApproved = 0;
		int amountDisapproved = 0;
		int amountAssigned = 0;
		int amountClosed = 0;

		Iterable<Ticket> tickets = ticketService.findAll();
		if (tickets != null) {
			for (Iterator<Ticket> iterator = tickets.iterator(); iterator.hasNext();) {
				Ticket item = (Ticket) iterator.next();
				switch (item.getStatus()) {
					case Approved:
						amountApproved++;
						break;
					case Assigned:
						amountAssigned++;
						break;
					case Closed:
						amountClosed++;
						break;
					case Disapproved:
						amountDisapproved++;
						break;
					case New:
						amountNew++;
						break;
					case Resolved:
						amountResolved++;
						break;
					default:
						break;
				}
			}
		}
		summary.setAmountApproved(amountApproved);
		summary.setAmountAssigned(amountAssigned);
		summary.setAmountClosed(amountClosed);
		summary.setAmountDisapproved(amountDisapproved);
		summary.setAmountNew(amountNew);
		summary.setAmountResolved(amountResolved);
		response.setData(summary);

		return ResponseEntity.ok(response);
	}

	@DeleteMapping(value = "{id}")
	@PreAuthorize("hasAnyRole('CUSTOMER')")
	public ResponseEntity<Response<String>> delete(@PathVariable("id") String id) {
		Response<String> response = new Response<>();
		Ticket ticket = ticketService.findById(id);
		if (ticket == null) {
			response.getErrors().add("Register not found " + id);
			return ResponseEntity.badRequest().body(response);
		}

		ticketService.delete(id);
		response.setData("Object Deleted");
		return ResponseEntity.ok(response);
	}

	private Integer generateNumber() {
		Random random = new Random();
		return random.nextInt(999999);
	}

	private User userFromRequest(HttpServletRequest req) {
		String token = req.getHeader("Authorization");
		String email = jwtTokenUtil.getUsernameFromToken(token);

		return userService.findByEmail(email);
	}

	private void validaCreateTicket(Ticket ticket, BindingResult result) {
		if (ticket.getTitle() == null) {
			result.addError(new ObjectError("Ticket", "Title field can't be null"));
		}
	}

	private void validaUpdateTicket(Ticket ticket, BindingResult result) {
		if (ticket.getId() == null) {
			result.addError(new ObjectError("Ticket", "Id field can't be null"));
		}

		if (ticket.getTitle() == null) {
			result.addError(new ObjectError("Ticket", "Title field can't be null"));
		}
	}

	private void validaChangeStatusTicket(String id, String status, BindingResult result) {
		if (id == null || id.isEmpty()) {
			result.addError(new ObjectError("Ticket", "Id field can't be null"));
		}

		if (status == null || status.isEmpty()) {
			result.addError(new ObjectError("Ticket", "Status field can't be null"));
		}
	}

}
