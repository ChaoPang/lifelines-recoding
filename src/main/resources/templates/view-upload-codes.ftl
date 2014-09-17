<#include "template-header.ftl">
<#include "template-footer.ftl">
<@header/>
<form method="POST" enctype="multipart/form-data">
	<div class="row-fluid">
		<div class="span12">
		<center><h3>Upload new codes</h3></center>
		</div>
	</div><br>
	<div class="row-fluid">
		<div class="span12">
			<center>Upload new dictionary containing codes and name of activities in <u>excel</u> format</center>
		</div>
	</div><br>
	<div class="row-fluid">
		<div class="span12">
			<center>
				<div class="fileupload fileupload-new" data-provides="fileupload">
					<div class="input-append">
						<div class="uneditable-input">
							<i class="icon-file fileupload-exists"></i>
							<span class="fileupload-preview"></span>
						</div>
						<span class="btn btn-file btn-info">
							<span class="fileupload-new">Select file</span>
							<span class="fileupload-exists">Change</span>
							<input type="file" name="file" required/>
						</span>
						<a href="#" class="btn btn-danger fileupload-exists" data-dismiss="fileupload">Remove</a>
						<button id="upload-button" class="btn btn-primary" type="button">Upload</button>
					</div>
				</div>
			</center>
		</div>
	</div><br><br>
	<div class="row-fluid">
		<div class="offset3 span6">
			<#if message ??><div class="alert alert-error"><strong>Error : </strong> ${message} <button type="button" class="close" data-dismiss="alert">&times;</button> </div> </#if>
			<table class="table table-bordered">
				<thead>
					<tr>
						<th class="equal-width-th">Name</th>
						<th class="equal-width-th">Code</th>
						<th class="equal-width-th">CodeSystem</th>
					</tr>
				</thead>
				<tbody>
					<tr><td>MOUNTAINBIKE</td><td>01009</td><td>MET</td></tr>
					<tr><td>WIELRENNEN</td><td>01040</td><td>MET</td></tr>
					<tr><td>RACEFIETS</td><td>01040</td><td>MET</td></tr>
					<tr><td>SPINNEN</td><td>02019</td><td>MET</td></tr>
				</tbody>
			</table>
		</div>
	</div>
</form>
<script>
	$( document ).ready(function(){
		$('#upload-button').click(function(event){
			$('form:first').attr('action','/add/upload').submit();
		});
	});
</script>
<@footer/>